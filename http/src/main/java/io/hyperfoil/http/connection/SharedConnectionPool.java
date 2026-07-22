package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;

import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.config.ConnectionPoolConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * This instance is not thread-safe as it should be accessed only the {@link #executor()}.
 */
public class SharedConnectionPool extends ConnectionPoolStats implements HttpConnectionPool {
   private static final Logger log = LogManager.getLogger(SharedConnectionPool.class);
   private static final boolean trace = log.isTraceEnabled();
   //TODO: configurable
   private static final int MAX_FAILURES = 100;

   private final HttpClientPoolImpl clientPool;
   private final ArrayList<HttpConnection> connections = new ArrayList<>();
   private final ArrayDeque<HttpConnection> available;
   private final List<HttpConnection> temporaryInFlight;
   private final ConnectionReceiver handleNewConnection = this::handleNewConnection;
   private final Runnable checkCreateConnections = this::checkCreateConnections;
   private final Runnable onConnectFailure = this::onConnectFailure;
   private final ConnectionPoolConfig sizeConfig;
   private final EventLoop eventLoop;

   private int connecting; // number of connections being opened
   private int created;
   private int closed; // number of closed connections in #connections
   private int availableClosed; // connections closed but staying in available queue
   private int failures;
   private Handler<AsyncResult<Void>> startedHandler;
   private boolean shutdown;
   private final Deque<ConnectionConsumer> waiting = new ArrayDeque<>();
   private ScheduledFuture<?> pulseFuture;
   private ScheduledFuture<?> keepAliveFuture;
   private boolean shouldPulse = true;
   /**
    * Connections that were in-flight when a !shouldPulse drain ran and were therefore skipped.
    * shouldPulse is only restored to true once all of them have finished their current request
    * and called release() with afterRequest=true.
    */
   private final Set<HttpConnection> pendingDrainConnections = new HashSet<>();

   SharedConnectionPool(HttpClientPoolImpl clientPool, EventLoop eventLoop, ConnectionPoolConfig sizeConfig) {
      super(clientPool.authority);
      this.clientPool = clientPool;
      this.sizeConfig = sizeConfig;
      this.eventLoop = eventLoop;
      this.available = new ArrayDeque<>(sizeConfig.max());
      this.temporaryInFlight = new ArrayList<>(sizeConfig.max());
   }

   @Override
   public HttpClientPool clientPool() {
      return clientPool;
   }

   private HttpConnection acquireNow(boolean exclusiveConnection) {
      assert eventLoop.inEventLoop();
      try {
         for (;;) {
            HttpConnection connection = available.pollFirst();
            if (connection == null) {
               if (log.isTraceEnabled()) {
                  log.trace("No connection to {} available, currently used {}", authority, usedConnections.current());
               }
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
               connection.onAcquire();
               if (log.isDebugEnabled()) {
                  log.debug("acquireNow: acquired {} to {}, inFlight={} (after), connection.inFlight={}",
                        connection, authority, inFlight.current(), connection.inFlight());
               }
               return connection;
            } else {
               availableClosed--;
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
      if (log.isDebugEnabled()) {
         log.debug("acquire: {} exclusiveConnection={}, waiting={}, available={}, inFlight={}",
               authority, exclusiveConnection, waiting.size(), available.size(), inFlight.current());
      }
      HttpConnection connection = acquireNow(exclusiveConnection);
      if (connection != null) {
         consumer.accept(connection);
         checkCreateConnections();
      } else {
         if (failures > MAX_FAILURES) {
            log.error("The request cannot be made since the failures to connect to {} exceeded a threshold. Stopping session.",
                  authority);
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
         if (connection.inFlight() == 0) {
            // The request was not executed in the end (response was cached)
            available.addFirst(connection);
         } else {
            available.addLast(connection);
         }
      }
   }

   @Override
   public void release(HttpConnection connection, boolean becameAvailable, boolean afterRequest) {
      assert executor().inEventLoop();
      if (trace) {
         log.trace("Release {} (became available={} after request={})", connection, becameAvailable, afterRequest);
      }
      if (log.isDebugEnabled()) {
         boolean alreadyInAvailable = available.contains(connection);
         log.debug(
               "release: {} becameAvailable={}, afterRequest={}, connection.inFlight={}, inFlight={} (before), available={}, alreadyInAvailable={}",
               connection, becameAvailable, afterRequest, connection.inFlight(), inFlight.current(), available.size(),
               alreadyInAvailable);
      }
      if (becameAvailable) {
         if (!connection.isClosed()) {
            if (connection.inFlight() == 0) {
               // We are adding to the beginning of the queue to prefer reusing connections rather than cycling
               // too many often-idle connections
               assert !available.contains(connection);
               available.addFirst(connection);
            } else {
               assert !available.contains(connection);
               available.addLast(connection);
            }
         }
      }
      if (afterRequest) {
         inFlight.decrementUsed();
         // Only relevant during drain: if this connection was deferred, remove it from the pending set.
         if (!shouldPulse && pendingDrainConnections.remove(connection) && pendingDrainConnections.isEmpty()) {
            shouldPulse = true;
            // Drain any sessions that queued up while we were waiting for in-flight connections.
            if (!waiting.isEmpty()) {
               pulse();
            }
         }
      }
      if (connection.inFlight() == 0) {
         usedConnections.decrementUsed();
      }
      if (keepAliveFuture == null && sizeConfig.keepAliveTime() > 0) {
         long lastUsed = available.stream().filter(c -> !c.isClosed()).mapToLong(HttpConnection::lastUsed).min()
               .orElse(connection.lastUsed());
         long nextCheck = sizeConfig.keepAliveTime() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastUsed);
         log.debug("Scheduling next keep-alive check in {} ms", nextCheck);
         keepAliveFuture = eventLoop.schedule(() -> {
            long now = System.nanoTime();
            // We won't removing closed connections from available: acquire() will eventually remove these
            for (HttpConnection c : available) {
               if (c.isClosed())
                  continue;
               long idleTime = TimeUnit.NANOSECONDS.toMillis(now - c.lastUsed());
               if (idleTime > sizeConfig.keepAliveTime()) {
                  c.close();
               }
            }
            keepAliveFuture = null;
         }, nextCheck, TimeUnit.MILLISECONDS);
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
      assert executor().inEventLoop();
      if (trace) {
         log.trace("Pulse to {} ({} waiting)", authority, waiting.size());
      }
      if (log.isDebugEnabled()) {
         log.debug("pulse: {} shouldPulse={}, waiting={}, available={}, inFlight={}",
               authority, shouldPulse, waiting.size(), available.size(), inFlight.current());
      }
      // session terminated, there is nothing that we can do
      if (!shouldPulse) {
         // If we are already waiting for in-flight connections to drain, do not re-enter the drain
         // setup — release() will restore shouldPulse once the last pending connection returns.
         if (!pendingDrainConnections.isEmpty()) {
            return;
         }

         if (log.isDebugEnabled()) {
            log.debug("pulse: session terminated, releasing all non-available connections, waiting={}", waiting.size());
         }

         blockedSessions.decrementUsed(waiting.size());
         waiting.clear();

         assert blockedSessions.current() == 0;

         for (HttpConnection conn : connections) {
            // The following if/else block explicitly maps out the connection's lifecycle history,
            // making edge cases easier to trace and debug.

            // Only add to available if not already there and not still in-flight:
            // in-flight connections will add themselves via releasePoolAndPulse() once
            // their response arrives, so forcing becameAvailable=true here would create a duplicate.
            if (available.contains(conn)) {
               // already in the queue, skip
            } else if (conn.pendingRequestCount() > 0) {
               // Real HTTP requests are on the wire — wait for the server response.
               pendingDrainConnections.add(conn);
            } else if (conn.inFlight() > 0) {
               // Only aboutToSend > 0: the consumer that acquired this connection is gone (session was
               // reset before it could call request()). No HTTP request was ever sent, so this is NOT
               // an afterRequest event. We manually undo the pool-level inFlight increment that
               // acquireNow() made, then return the connection as available with afterRequest=false.
               // Passing afterRequest=true here would be semantically wrong: it signals a completed
               // HTTP request, which never happened, and would incorrectly trigger any per-request
               // hooks (stats, pendingDrainConnections removal, etc.) inside release().
               cancelAcquire(conn);
            } else {
               release(conn, true, false);
            }
         }
         if (pendingDrainConnections.isEmpty()) {
            // No in-flight connections to wait for — restore immediately.
            shouldPulse = true;
         }
         // else: shouldPulse stays false until release() drains pendingDrainConnections
         return;
      }
      ConnectionConsumer consumer = waiting.poll();
      if (consumer != null) {
         HttpConnection connection = acquireNow(false);
         if (connection != null) {
            blockedSessions.decrementUsed();
            consumer.accept(connection);
         } else if (failures > MAX_FAILURES) {
            log.error("The request cannot be made since the failures to connect to {} exceeded a threshold. Stopping session.",
                  authority);
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

   /** Returns the current number of entries in the available deque (for testing). */
   public int availableCount() {
      return available.size();
   }

   @Override
   public void cancelAcquire(HttpConnection connection) {
      assert executor().inEventLoop();
      connection.cancelAcquire();
      inFlight.decrementUsed();
      release(connection, true, false);
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
            String failureMessage = String.format("Cannot connect to %s: %d created, %d failures.", authority, created,
                  failures);
            if (created > 0) {
               failureMessage += " Hint: either configure SUT to accept more open connections or reduce http.sharedConnections.";
            }
            handler.handle(Future.failedFuture(failureMessage));
         }
         pulse();
         return;
      }
      if (needsMoreConnections()) {
         connecting++;
         clientPool.connect(this, handleNewConnection);
         eventLoop.schedule(checkCreateConnections, 2, TimeUnit.MILLISECONDS);
      }
   }

   private boolean needsMoreConnections() {
      return created + connecting < sizeConfig.core() || (created + connecting < sizeConfig.max()
            && connecting + available.size() - availableClosed < sizeConfig.buffer());
   }

   private void handleNewConnection(HttpConnection conn, Throwable err) {
      // at this moment we're in unknown thread
      if (err != null) {
         // scheduling task when the executor is shut down causes errors
         if (!eventLoop.isShuttingDown() && !eventLoop.isShutdown()) {
            // Accessing created & failures is unreliable - we need to access those in eventloop thread.
            // For logging, though, we won't care.
            log.warn(new FormattedMessage("Cannot create connection to {} (created: {}, failures: {})", authority, created,
                  failures + 1), err);
            eventLoop.execute(onConnectFailure);
         }
      } else {
         // we are using this eventloop as the bootstrap group so the connection should be created for us
         assert conn.context().executor() == eventLoop;
         assert eventLoop.inEventLoop();

         Handler<AsyncResult<Void>> handler = null;
         connections.add(conn);
         connecting--;
         created++;
         // With each success we reset the counter - otherwise we'd eventually
         // stop trying to create new connections and the sessions would be stuck.
         failures = 0;
         available.add(conn);
         log.debug("Created {} to {} ({}+{}=?{}:{}/{})", conn, authority,
               created, connecting, connections.size(), available.size() - availableClosed, sizeConfig.max());

         incrementTypeStats(conn);

         conn.context().channel().closeFuture().addListener(v -> {
            conn.setClosed();
            log.debug("Closed {} to {}. ({}+{}=?{}:{}/{})", conn, authority,
                  created, connecting, connections.size(), available.size() - availableClosed, sizeConfig.max());
            created--;
            closed++;
            if (available.contains(conn)) {
               availableClosed++;
            }
            typeStats.get(tagConnection(conn)).decrementUsed();
            if (!shutdown) {
               if (closed >= sizeConfig.max()) {
                  // do cleanup
                  connections.removeIf(HttpConnection::isClosed);
                  closed = 0;
               }
               checkCreateConnections();
            }
         });

         if (needsMoreConnections()) {
            checkCreateConnections();
         } else {
            if (startedHandler != null && created >= sizeConfig.core()) {
               handler = startedHandler;
               startedHandler = null;
            }
         }

         if (handler != null) {
            handler.handle(Future.succeededFuture());
         }
         pulse();
      }
   }

   private void onConnectFailure() {
      failures++;
      connecting--;
      eventLoop.schedule(checkCreateConnections, 50, TimeUnit.MILLISECONDS);
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

   @Override
   public void onSessionTryTerminate() {
      if (!executor().inEventLoop()) {
         executor().execute(this::onSessionTryTerminate);
         return;
      }
      if (log.isDebugEnabled()) {
         log.debug("onSessionTryTerminate: {} inFlight={}, available={}, waiting={}",
               authority, inFlight.current(), available.size(), waiting.size());
      }
      this.shouldPulse = false;
   }
}
