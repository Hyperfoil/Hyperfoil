package io.hyperfoil.http;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.http.config.Http;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class BaseMockConnection implements HttpConnection {
   @Override
   public void attach(HttpConnectionPool pool) {
   }

   @Override
   public void request(HttpRequest request, BiConsumer<Session, HttpRequestWriter>[] headerAppenders, boolean injectHostHeader,
         BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
   }

   @Override
   public HttpRequest dispatchedRequest() {
      return null;
   }

   @Override
   public HttpRequest peekRequest(int streamId) {
      return null;
   }

   @Override
   public boolean removeRequest(int streamId, HttpRequest request) {
      return false;
   }

   @Override
   public void setClosed() {
   }

   @Override
   public boolean isOpen() {
      return false;
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
   public HttpVersion version() {
      return null;
   }

   @Override
   public Http config() {
      return null;
   }

   @Override
   public HttpConnectionPool pool() {
      return null;
   }

   @Override
   public long lastUsed() {
      return 0;
   }

   @Override
   public ChannelHandlerContext context() {
      return null;
   }

   @Override
   public void onAcquire() {
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
