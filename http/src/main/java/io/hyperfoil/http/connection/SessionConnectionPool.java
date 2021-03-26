package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;

import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SessionConnectionPool implements HttpConnectionPool {
   private static final Logger log = LogManager.getLogger(SessionConnectionPool.class);
   private static final boolean trace = log.isTraceEnabled();
   private final HttpConnectionPool shared;
   private final ArrayDeque<HttpConnection> available;
   private final ArrayList<HttpConnection> owned;

   public SessionConnectionPool(HttpConnectionPool shared, int capacity) {
      this.shared = shared;
      this.available = new ArrayDeque<>(capacity);
      this.owned = new ArrayList<>(capacity);
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
            connection.onAcquire();
            consumer.accept(connection);
            return;
         }
      }
   }

   @Override
   public void afterRequestSent(HttpConnection connection) {
      // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
      if (connection.isAvailable()) {
         log.trace("Keeping connection in session-local pool {}.", connection);
         available.addLast(connection);
      }
      // HashSet would be probably allocating
      if (!owned.contains(connection)) {
         owned.add(connection);
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
      if (trace) {
         log.trace("Releasing to session-local pool {} ({}, {})", connection, becameAvailable, afterRequest);
      }
      if (becameAvailable) {
         log.trace("Added connection to session-local pool.");
         available.addLast(connection);
      }
      if (afterRequest) {
         if (connection.isOpen()) {
            shared.decrementInFlight();
         } else {
            shared.release(connection, becameAvailable, true);
            owned.remove(connection);
         }
      }
   }

   @Override
   public void onSessionReset() {
      for (int i = owned.size() - 1; i >= 0; --i) {
         HttpConnection connection = owned.get(i);
         if (connection.inFlight() == 0) {
            shared.release(connection, !connection.isClosed(), false);
         } else {
            connection.close();
         }
      }
      owned.clear();
      available.clear();
   }

   @Override
   public void incrementInFlight() {
      // noone should delegate to SessionConnectionPool
      throw new UnsupportedOperationException();
   }

   @Override
   public void decrementInFlight() {
      // noone should delegate to SessionConnectionPool
      throw new UnsupportedOperationException();
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
