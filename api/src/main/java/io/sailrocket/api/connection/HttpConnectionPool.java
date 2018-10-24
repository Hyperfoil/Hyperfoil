package io.sailrocket.api.connection;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.api.session.Session;

public interface HttpConnectionPool {
   HttpRequest request(HttpMethod method, String path, ByteBuf body);

   void registerWaitingSession(Session session);

   EventExecutor executor();

   void pulse();
}
