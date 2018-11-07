package io.sailrocket.core.client.netty;

import java.util.function.BiConsumer;

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Settings;

class Http2ConnectionHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<CustomHttp2ConnectionHandler, Http2ConnectionHandlerBuilder> {

  private final HttpClientPoolImpl clientPool;
  private final BiConsumer<HttpConnection, Throwable> requestHandler;

  public Http2ConnectionHandlerBuilder(HttpClientPoolImpl clientPool, BiConsumer<HttpConnection, Throwable> requestHandler) {
    this.clientPool = clientPool;
    this.requestHandler = requestHandler;
    initialSettings(clientPool.http2Settings);
  }

  @Override
  protected CustomHttp2ConnectionHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
    return new CustomHttp2ConnectionHandler(clientPool, requestHandler, decoder, encoder, initialSettings);
  }

  public CustomHttp2ConnectionHandler build(io.netty.handler.codec.http2.Http2Connection conn) {
    connection(conn);
    frameListener(new Http2EventAdapter() { /* Dunno why this is needed */ });
    return super.build();
  }
}
