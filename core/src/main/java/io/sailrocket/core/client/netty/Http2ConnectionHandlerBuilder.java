package io.sailrocket.core.client.netty;

import java.util.function.BiConsumer;

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Settings;
import io.sailrocket.api.connection.HttpConnection;
import io.sailrocket.api.connection.HttpConnectionPool;

class Http2ConnectionHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<CustomHttp2ConnectionHandler, Http2ConnectionHandlerBuilder> {

  private final HttpConnectionPool connectionPool;
  private final BiConsumer<HttpConnection, Throwable> requestHandler;

  public Http2ConnectionHandlerBuilder(HttpConnectionPool connectionPool, BiConsumer<HttpConnection, Throwable> requestHandler) {
    this.connectionPool = connectionPool;
    this.requestHandler = requestHandler;
  }

  @Override
  protected CustomHttp2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
    return new CustomHttp2ConnectionHandler(connectionPool, requestHandler, decoder, encoder, initialSettings);
  }

  public CustomHttp2ConnectionHandler build(io.netty.handler.codec.http2.Http2Connection conn) {
    connection(conn);
    frameListener(new Http2EventAdapter() { /* Dunno why this is needed */ });
    return super.build();
  }
}
