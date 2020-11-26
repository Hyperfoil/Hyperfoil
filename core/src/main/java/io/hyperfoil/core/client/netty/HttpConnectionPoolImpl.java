package io.hyperfoil.core.client.netty;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This instance is not thread-safe as it should be accessed only the {@link #executor()}.
 */
class HttpConnectionPoolImpl implements HttpConnectionPool {
   private static final Logger log = LoggerFactory.getLogger(HttpConnectionPoolImpl.class);
   private static final boolean trace = log.isTraceEnabled();
   //TODO: configurable
   private static final int MAX_FAILURES = 100;

   private final HttpClientPoolImpl clientPool;
   private final ArrayList<HttpConnection> connections = new ArrayList<>();
   private final ArrayDeque<HttpConnection> available;
   private final List<HttpConnection> temporaryInFlight;
   private final int size;
   private final EventLoop eventLoop;
   private int count; // The estimated count : created + creating
   private int created;
   private int closed; // number of closed connections in #connections
   private int failures;
   private Handler<AsyncResult<Void>> startedHandler;
   private boolean shutdown;
   private Deque<Session> waitingSessions = new ArrayDeque<>();
   private ScheduledFuture<?> pulseFuture;

   HttpConnectionPoolImpl(HttpClientPoolImpl clientPool, EventLoop eventLoop, int size) {
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

   @Override
   public boolean request(HttpRequest request,
                          BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          boolean injectHostHeader,
                          BiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          boolean exclusiveConnection) {
      assert eventLoop.inEventLoop();
      if (request.session.currentRequest() != null) {
         // Refuse to fire request from other request's handler as the other handlers
         // would have messed up current request in session.
         // Handlers must not block anyway, so this is illegal way to run request
         // and happens only with programmatic configuration in testsuite.
         log.error("#{} Invoking request directly from a request handler; current: {}, requested {}", new IllegalStateException(),
               request.session.uniqueId(), request.session.currentRequest(), request);
         return false;
      }
      HttpConnection connection;
      try {
         for (; ; ) {
            connection = available.pollFirst();
            if (connection == null) {
               log.debug("No connection to {} available", clientPool.authority);
               if (failures > MAX_FAILURES) {
                  log.error("The request cannot be made since the failures to connect to {} exceeded a threshold. Stopping session.", clientPool.authority);
                  request.session.stop();
               }
               return false;
            } else if (!connection.isClosed()) {
               if (exclusiveConnection && connection.inFlight() > 0) {
                  temporaryInFlight.add(connection);
                  continue;
               }
               request.attach(connection);
               connection.attach(this);
               connection.request(request, headerAppenders, injectHostHeader, bodyGenerator);
               // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
               if (!exclusiveConnection && connection.isAvailable()) {
                  available.addLast(connection);
               }
               return true;
            } else {
               log.trace("Connection {} to {} is already closed", connection, clientPool.authority);
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
   public void release(HttpConnection connection) {
      available.add(connection);
   }

   @Override
   public void onSessionReset() {
      // noop
   }

   @Override
   public void registerWaitingSession(Session session) {
      waitingSessions.add(session);
   }

   @Override
   public int waitingSessions() {
      return waitingSessions.size();
   }

   @Override
   public EventLoop executor() {
      return eventLoop;
   }

   @Override
   public void pulse() {
      Session session = waitingSessions.poll();
      if (trace) {
         log.trace("Pulse #{} to {} ({} waiting)", session == null ? "<none>" : session.uniqueId(), clientPool.authority, waitingSessions.size());
      }
      if (session != null) {
         session.proceed();
      }
      // The session might not use the connection (e.g. when it's terminated) and call pulse() again
      // We don't want to activate all the sessions, though so we need to schedule another pulse
      if (pulseFuture == null && !waitingSessions.isEmpty()) {
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
            String failureMessage = String.format("Cannot connect to %s: %d created, %d failures.", clientPool.authority, created, failures);
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
         clientPool.connect(this, (conn, err) -> {
            // at this moment we're in unknown thread
            if (err != null) {
               count--;
               failures++;
               // scheduling task when the executor is shut down causes errors
               if (!eventLoop.isShuttingDown() && !eventLoop.isShutdown()) {
                  log.warn("Cannot create connection to {} (created: {}, failures: {})", err, clientPool.authority, created, failures);
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
               log.debug("Connection {} to {} created ({}->{}=?{}:{}/{})", conn, clientPool.authority, count, created, connections.size(), available.size(), size);

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
                  log.debug("Connection {} to {} closed. ({}->{}=?{}:{}/{})", conn, clientPool.authority, count, created, connections.size(), available.size(), size);
                  count--;
                  created--;
                  closed++;
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
         });
         eventLoop.schedule(() -> checkCreateConnections(), 2, TimeUnit.MILLISECONDS);
      }
   }

   void start(Handler<AsyncResult<Void>> handler) {
      startedHandler = handler;
      eventLoop.execute(() -> checkCreateConnections());
   }

   void shutdown() {
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
