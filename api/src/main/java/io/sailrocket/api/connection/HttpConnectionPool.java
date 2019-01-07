package io.sailrocket.api.connection;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;

public interface HttpConnectionPool {
   HttpClientPool clientPool();

   boolean request(Request request,
                   HttpMethod method, Function<Session, String> pathGenerator,
                   BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                   BiFunction<Session, Connection, ByteBuf> bodyGenerator);

   void registerWaitingSession(Session session);

   EventExecutor executor();

   void pulse();

   Collection<? extends HttpConnection> connections();

   void release(HttpConnection connection);
}
