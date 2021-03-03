package io.hyperfoil.http.api;

import java.util.Collection;

import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.netty.channel.EventLoop;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface HttpConnectionPool {
   HttpClientPool clientPool();

   void acquire(boolean exclusiveConnection, ConnectionConsumer consumer);

   void afterRequestSent(HttpConnection connection);

   int waitingSessions();

   EventLoop executor();

   void pulse();

   Collection<? extends HttpConnection> connections();

   void release(HttpConnection connection, boolean becameAvailable, boolean afterRequest);

   void onSessionReset();

   void incrementInFlight();

   void decrementInFlight();

   void visitConnectionStats(ConnectionStatsConsumer consumer);

   void start(Handler<AsyncResult<Void>> handler);

   void shutdown();
}
