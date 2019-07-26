package io.hyperfoil.api.connection;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.hyperfoil.api.session.Session;

public interface HttpConnectionPool {
   HttpClientPool clientPool();

   boolean request(HttpRequest request,
                   BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                   BiFunction<Session, Connection, ByteBuf> bodyGenerator);

   void registerWaitingSession(Session session);

   int waitingSessions();

   EventLoop executor();

   void pulse();

   Collection<? extends HttpConnection> connections();

   void release(HttpConnection connection);
}
