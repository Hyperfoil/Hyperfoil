package io.hyperfoil.core.client.netty;

import java.util.function.BiConsumer;

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Settings;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;

class Http2ConnectionHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<CustomHttp2ConnectionHandler, Http2ConnectionHandlerBuilder> {

  private final HttpConnectionPool connectionPool;
  private final boolean isUpgrade;
  private final BiConsumer<HttpConnection, Throwable> requestHandler;

  Http2ConnectionHandlerBuilder(HttpConnectionPool connectionPool, boolean isUpgrade, BiConsumer<HttpConnection, Throwable> requestHandler) {
    this.connectionPool = connectionPool;
    this.isUpgrade = isUpgrade;
    this.requestHandler = requestHandler;
  }

  @Override
  protected CustomHttp2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
    return new CustomHttp2ConnectionHandler(connectionPool, requestHandler, decoder, encoder, initialSettings, isUpgrade);
  }

  public CustomHttp2ConnectionHandler build(io.netty.handler.codec.http2.Http2Connection conn) {
    connection(conn);
    frameListener(new Http2EventAdapter() { /* Dunno why this is needed */ });
    return super.build();
  }
}
