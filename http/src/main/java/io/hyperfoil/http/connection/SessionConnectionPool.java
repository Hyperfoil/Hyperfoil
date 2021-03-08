package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.Collection;

import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SessionConnectionPool implements HttpConnectionPool {
   private static final Logger log = LoggerFactory.getLogger(SessionConnectionPool.class);
   private final HttpConnectionPool shared;
   private final ArrayDeque<HttpConnection> available = new ArrayDeque<>();

   public SessionConnectionPool(HttpConnectionPool shared) {
      this.shared = shared;
   }

   @Override
   public HttpClientPool clientPool() {
      return shared.clientPool();
   }

   @Override
   public void acquire(boolean exclusiveConnection, ConnectionConsumer consumer) {
      assert !exclusiveConnection;
      for (; ; ) {
         HttpConnection connection = available.pollFirst();
         if (connection == null) {
            shared.acquire(true, consumer);
            return;
         } else if (!connection.isClosed()) {
            shared.incrementInFlight();
            consumer.accept(connection);
            return;
         }
      }
   }

   @Override
   public void afterRequestSent(HttpConnection connection) {
      // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
      if (connection.isAvailable()) {
         log.trace("Keeping connection in session-local pool.");
         available.addLast(connection);
      }
   }

   @Override
   public int waitingSessions() {
      return shared.waitingSessions();
   }

   @Override
   public EventLoop executor() {
      return shared.executor();
   }

   @Override
   public void pulse() {
      shared.pulse();
   }

   @Override
   public Collection<? extends HttpConnection> connections() {
      return available;
   }

   @Override
   public void release(HttpConnection connection, boolean becameAvailable, boolean afterRequest) {
      if (connection.isClosed()) {
         shared.release(connection, false, true);
      } else {
         shared.decrementInFlight();
      }
      if (becameAvailable) {
         log.trace("Added connection to session-local pool.");
         available.addLast(connection);
      }
   }

   @Override
   public void onSessionReset() {
      HttpConnection connection;
      while ((connection = available.pollFirst()) != null) {
         shared.release(connection, true, false);
      }
   }

   @Override
   public void incrementInFlight() {
      shared.incrementInFlight();
   }

   @Override
   public void decrementInFlight() {
      shared.decrementInFlight();
   }

   @Override
   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      // This should be never invoked because we're monitoring the shared pools only
      throw new UnsupportedOperationException();
   }

   @Override
   public void start(Handler<AsyncResult<Void>> handler) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void shutdown() {
      throw new UnsupportedOperationException();
   }
}
