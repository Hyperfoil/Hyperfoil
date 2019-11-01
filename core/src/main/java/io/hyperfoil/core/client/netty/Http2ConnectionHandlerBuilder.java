package io.hyperfoil.core.client.netty;

import java.util.function.BiConsumer;

import io.hyperfoil.api.connection.HttpClientPool;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Settings;
import io.hyperfoil.api.connection.HttpConnection;

class Http2ConnectionHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<CustomHttp2ConnectionHandler, Http2ConnectionHandlerBuilder> {

   private final HttpClientPool clientPool;
   private final boolean isUpgrade;
   private final BiConsumer<HttpConnection, Throwable> requestHandler;

   Http2ConnectionHandlerBuilder(HttpClientPool clientPool, boolean isUpgrade, BiConsumer<HttpConnection, Throwable> requestHandler) {
      this.clientPool = clientPool;
      this.isUpgrade = isUpgrade;
      this.requestHandler = requestHandler;
   }

   @Override
   protected CustomHttp2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
      return new CustomHttp2ConnectionHandler(clientPool, requestHandler, decoder, encoder, initialSettings, isUpgrade);
   }

   public CustomHttp2ConnectionHandler build(io.netty.handler.codec.http2.Http2Connection conn) {
      connection(conn);
      frameListener(new Http2EventAdapter() { /* Dunno why this is needed */});
      return super.build();
   }
}
