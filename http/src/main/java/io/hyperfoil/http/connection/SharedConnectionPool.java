package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This instance is not thread-safe as it should be accessed only the {@link #executor()}.
 */
class SharedConnectionPool extends ConnectionPoolStats implements HttpConnectionPool {
   private static final Logger log = LoggerFactory.getLogger(SharedConnectionPool.class);
   private static final boolean trace = log.isTraceEnabled();
   //TODO: configurable
   private static final int MAX_FAILURES = 100;

   private final HttpClientPoolImpl clientPool;
   private final ArrayList<HttpConnection> connections = new ArrayList<>();
   private final ArrayDeque<HttpConnection> available;
   private final List<HttpConnection> temporaryInFlight;
   private final ConnectionReceiver handleNewConnection = this::handleNewConnection;
   private final Runnable checkCreateConnections = this::checkCreateConnections;
   private final int size;
   private final EventLoop eventLoop;

   private int count; // The estimated count : created + creating
   private int created;
   private int closed; // number of closed connections in #connections
   private int failures;
   private Handler<AsyncResult<Void>> startedHandler;
   private boolean shutdown;
   private final Deque<ConnectionConsumer> waiting = new ArrayDeque<>();
   private ScheduledFuture<?> pulseFuture;

   SharedConnectionPool(HttpClientPoolImpl clientPool, EventLoop eventLoop, int size) {
      super(clientPool.authority);
      this.clientPool = clientPool;
      this.size = size;
      this.eventLoop = eventLoop;
      this.available = new ArrayDeque<>(size);
      this.temporaryInFlight = new ArrayList<>(size);
   }

   @Override
   public HttpClientPool clientPool() {
      return clientPool;
   }

   private HttpConnection acquireNow(boolean exclusiveConnection) {
      assert eventLoop.inEventLoop();
      try {
         for (; ; ) {
            HttpConnection connection = available.pollFirst();
            if (connection == null) {
               log.debug("No connection to {} available", authority);
               return null;
            } else if (!connection.isClosed()) {
               if (exclusiveConnection && connection.inFlight() > 0) {
                  temporaryInFlight.add(connection);
                  continue;
               }
               inFlight.incrementUsed();
               if (connection.inFlight() == 0) {
                  usedConnections.incrementUsed();
               }

               return connection;
            } else {
               log.trace("Connection {} to {} is already closed", connection, authority);
            }
         }
      } finally {
         if (!temporaryInFlight.isEmpty()) {
            available.addAll(temporaryInFlight);
            temporaryInFlight.clear();
         }
      }
   }

   @Override
   public void acquire(boolean exclusiveConnection, ConnectionConsumer consumer) {
      HttpConnection connection = acquireNow(exclusiveConnection);
      if (connection != null) {
         consumer.accept(connection);
      } else {
         if (failures > MAX_FAILURES) {
            log.error("The request cannot be made since the failures to connect to {} exceeded a threshold. Stopping session.", authority);
            consumer.accept(null);
            return;
         }
         waiting.add(consumer);
         blockedSessions.incrementUsed();
      }
   }

   @Override
   public void afterRequestSent(HttpConnection connection) {
      // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
      if (connection.isAvailable()) {
         available.addLast(connection);
      }
   }

   @Override
   public void release(HttpConnection connection, boolean becameAvailable, boolean afterRequest) {
      if (becameAvailable) {
         available.add(connection);
      }
      inFlight.decrementUsed();
      if (connection.inFlight() == 0) {
         usedConnections.decrementUsed();
      }
   }

   @Override
   public void onSessionReset() {
      // noop
   }

   @Override
   public int waitingSessions() {
      return waiting.size();
   }

   @Override
   public EventLoop executor() {
      return eventLoop;
   }

   @Override
   public void pulse() {
      if (trace) {
         log.trace("Pulse to {} ({} waiting)", authority, waiting.size());
      }
      ConnectionConsumer consumer = waiting.poll();
      if (consumer != null) {
         HttpConnection connection = acquireNow(false);
         if (connection != null) {
            blockedSessions.decrementUsed();
            consumer.accept(connection);
         } else if (failures > MAX_FAILURES) {
            log.error("The request cannot be made since the failures to connect to {} exceeded a threshold. Stopping session.", authority);
            consumer.accept(null);
         } else {
            waiting.addFirst(consumer);
         }
      }
      // The session might not use the connection (e.g. when it's terminated) and call pulse() again
      // We don't want to activate all the sessions, though so we need to schedule another pulse
      if (pulseFuture == null && !waiting.isEmpty()) {
         pulseFuture = executor().schedule(this::scheduledPulse, 1, TimeUnit.MILLISECONDS);
      }
   }

   // signature to match Callable
   private Object scheduledPulse() {
      pulseFuture = null;
      pulse();
      return null;
   }

   @Override
   public Collection<HttpConnection> connections() {
      return connections;
   }

   private void checkCreateConnections() {
      assert eventLoop.inEventLoop();

      if (shutdown) {
         return;
      }
      if (failures > MAX_FAILURES) {
         // When the connection is not available we'll let sessions see & terminate if it's past the duration
         Handler<AsyncResult<Void>> handler = this.startedHandler;
         if (handler != null) {
            startedHandler = null;
            String failureMessage = String.format("Cannot connect to %s: %d created, %d failures.", authority, created, failures);
            if (created > 0) {
               failureMessage += " Hint: either configure SUT to accept more open connections or reduce http.sharedConnections.";
            }
            handler.handle(Future.failedFuture(failureMessage));
         }
         pulse();
         return;
      }
      if (count < size) {
         count++;
         clientPool.connect(this, handleNewConnection);
         eventLoop.schedule(checkCreateConnections, 2, TimeUnit.MILLISECONDS);
      }
   }

   private void handleNewConnection(HttpConnection conn, Throwable err) {
      // at this moment we're in unknown thread
      if (err != null) {
         count--;
         failures++;
         // scheduling task when the executor is shut down causes errors
         if (!eventLoop.isShuttingDown() && !eventLoop.isShutdown()) {
            log.warn("Cannot create connection to {} (created: {}, failures: {})", err, authority, created, failures);
            eventLoop.execute(this::checkCreateConnections);
         }
      } else {
         // we are using this eventloop as the bootstrap group so the connection should be created for us
         assert conn.context().executor() == eventLoop;
         assert eventLoop.inEventLoop();

         Handler<AsyncResult<Void>> handler = null;
         connections.add(conn);
         created++;
         // With each success we reset the counter - otherwise we'd eventually
         // stop trying to create new connections and the sessions would be stuck.
         failures = 0;
         available.add(conn);
         log.debug("Connection {} to {} created ({}->{}=?{}:{}/{})", conn, authority, count, created, connections.size(), available.size(), size);

         incrementTypeStats(conn);

         if (count < size) {
            checkCreateConnections();
         } else {
            if (created == size) {
               handler = startedHandler;
               startedHandler = null;
            }
         }

         conn.context().channel().closeFuture().addListener(v -> {
            conn.setClosed();
            log.debug("Connection {} to {} closed. ({}->{}=?{}:{}/{})", conn, authority, count, created, connections.size(), available.size(), size);
            count--;
            created--;
            closed++;
            // TODO: this won't work if the connection is closed outside of request handler
            inFlight.decrementUsed(conn.inFlight() + 1);
            decrementStatsOnClose(conn);
            if (!shutdown) {
               if (closed > size) {
                  // do cleanup
                  connections.removeIf(HttpConnection::isClosed);
                  closed = 0;
               }
               checkCreateConnections();
            }
         });

         if (handler != null) {
            handler.handle(Future.succeededFuture());
         }
         pulse();
      }
   }

   @Override
   public void start(Handler<AsyncResult<Void>> handler) {
      startedHandler = handler;
      eventLoop.execute(checkCreateConnections);
   }

   @Override
   public void shutdown() {
      log.debug("Shutdown called");
      shutdown = true;
      if (eventLoop.isShutdown()) {
         // Already shutdown
         return;
      }
      eventLoop.execute(() -> {
         log.debug("Closing all connections");
         for (HttpConnection conn : connections) {
            if (conn.isClosed()) {
               continue;
            }
            conn.context().writeAndFlush(Unpooled.EMPTY_BUFFER);
            conn.context().close();
            conn.context().flush();
         }
      });
   }
}
