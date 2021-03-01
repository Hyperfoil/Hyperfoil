package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;

public class PrivateConnectionPool implements HttpConnectionPool {
   private final HttpConnectionPool parent;
   private final ArrayDeque<HttpConnection> available = new ArrayDeque<>();

   public PrivateConnectionPool(HttpConnectionPool parent) {
      this.parent = parent;
   }

   @Override
   public HttpClientPool clientPool() {
      return parent.clientPool();
   }

   @Override
   public boolean request(HttpRequest request,
                          BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          boolean injectHostHeader,
                          BiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          boolean reserveConnection) {
      assert !reserveConnection;
      for (; ; ) {
         HttpConnection connection = available.pollFirst();
         if (connection == null) {
            boolean success = parent.request(request, headerAppenders, injectHostHeader, bodyGenerator, true);
            if (success) {
               request.connection().attach(this);
            }
            return success;
         } else if (!connection.isClosed()) {
            request.attach(connection);
            connection.attach(this);
            connection.request(request, headerAppenders, injectHostHeader, bodyGenerator);
            // Move it to the back of the queue if it is still available (do not prefer it for subsequent requests)
            if (connection.isAvailable()) {
               available.addLast(connection);
            }
            return true;
         }
      }
   }

   @Override
   public void registerWaitingSession(Session session) {
      parent.registerWaitingSession(session);
   }

   @Override
   public int waitingSessions() {
      return parent.waitingSessions();
   }

   @Override
   public EventLoop executor() {
      return parent.executor();
   }

   @Override
   public void pulse() {
      parent.pulse();
   }

   @Override
   public Collection<? extends HttpConnection> connections() {
      return available;
   }

   @Override
   public void release(HttpConnection connection) {
      available.addLast(connection);
   }

   @Override
   public void onSessionReset() {
      HttpConnection connection;
      while ((connection = available.pollFirst()) != null) {
         parent.release(connection);
      }
   }

   @Override
   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      // This should be never invoked because we're monitoring the shared pools only
      throw new UnsupportedOperationException();
   }

   @Override
   public void incrementBlockedSessions() {
      parent.incrementBlockedSessions();
   }

   @Override
   public void decrementBlockedSessions() {
      parent.decrementBlockedSessions();
   }
}
