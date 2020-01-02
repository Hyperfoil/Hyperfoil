package io.hyperfoil.core.http;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class BaseMockConnection implements HttpConnection {
   @Override
   public void attach(HttpConnectionPool pool) {
   }

   @Override
   public void request(HttpRequest request, BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
   }

   @Override
   public HttpRequest peekRequest(int streamId) {
      return null;
   }

   @Override
   public void setClosed() {
   }

   @Override
   public boolean isClosed() {
      return false;
   }

   @Override
   public boolean isSecure() {
      return false;
   }

   @Override
   public ChannelHandlerContext context() {
      return null;
   }

   @Override
   public boolean isAvailable() {
      return false;
   }

   @Override
   public int inFlight() {
      return 0;
   }

   @Override
   public void close() {
   }

   @Override
   public String host() {
      return null;
   }
}
