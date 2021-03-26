package io.hyperfoil.http.connection;

import java.util.Collection;
import java.util.Collections;

import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class ConnectionAllocator extends ConnectionPoolStats implements HttpConnectionPool {
   private static final Logger log = LogManager.getLogger(ConnectionAllocator.class);
   private final HttpClientPoolImpl clientPool;
   private final EventLoop eventLoop;

   ConnectionAllocator(HttpClientPoolImpl clientPool, EventLoop eventLoop) {
      super(clientPool.authority);
      this.clientPool = clientPool;
      this.eventLoop = eventLoop;
   }

   @Override
   public HttpClientPool clientPool() {
      return clientPool;
   }

   @Override
   public void acquire(boolean exclusiveConnection, ConnectionConsumer consumer) {
      log.trace("Creating connection to {}", authority);
      blockedSessions.incrementUsed();
      clientPool.connect(this, (conn, err) -> {
         if (err != null) {
            log.error("Cannot create connection to " + authority, err);
            // TODO retry couple of times?
            blockedSessions.decrementUsed();
            consumer.accept(null);
         } else {
            log.debug("Created {} to {}", conn, authority);
            blockedSessions.decrementUsed();
            inFlight.incrementUsed();
            usedConnections.incrementUsed();
            incrementTypeStats(conn);
            conn.onAcquire();

            conn.context().channel().closeFuture().addListener(v -> {
               conn.setClosed();
               log.debug("Closed {} to {}", conn, authority);
               typeStats.get(tagConnection(conn)).decrementUsed();
               usedConnections.decrementUsed();
            });
            consumer.accept(conn);
         }
      });
   }

   @Override
   public void afterRequestSent(HttpConnection connection) {
   }

   @Override
   public int waitingSessions() {
      return blockedSessions.current();
   }

   @Override
   public EventLoop executor() {
      return eventLoop;
   }

   @Override
   public void pulse() {
   }

   @Override
   public Collection<? extends HttpConnection> connections() {
      return Collections.emptyList();
   }

   @Override
   public void release(HttpConnection connection, boolean becameAvailable, boolean afterRequest) {
      if (afterRequest) {
         decrementInFlight();
      }
      connection.close();
   }

   @Override
   public void onSessionReset() {
   }

   @Override
   public void start(Handler<AsyncResult<Void>> handler) {
      handler.handle(Future.succeededFuture());
   }

   @Override
   public void shutdown() {
      // noop
   }
}
