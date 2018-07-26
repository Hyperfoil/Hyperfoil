package io.sailrocket.core.client.netty;

import io.sailrocket.core.client.HttpMethod;
import io.sailrocket.core.client.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Request implements HttpRequest {

  private final Http2Connection conn;
  private final Http2Headers headers;
  private boolean sent;
  final HttpMethod method;
  IntConsumer headersHandler;
  Consumer<ByteBuf> dataHandler;
  IntConsumer resetHandler;
  Consumer<Void> endHandler;

  Http2Request(Http2Client client, Http2Connection conn, HttpMethod method, String path) {
    this.method = method;
    this.conn = conn;
    this.headers = client.headers(method.name(), "https", path);
  }

  public Http2Request putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  @Override
  public HttpRequest headersHandler(IntConsumer handler) {
    headersHandler = handler;
    return this;
  }

  public Http2Request dataHandler(Consumer<ByteBuf> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public HttpRequest resetHandler(IntConsumer handler) {
    resetHandler = handler;
    return this;
  }

  public Http2Request endHandler(Consumer<Void> handler) {
    endHandler = handler;
    return this;
  }

  public void end(ByteBuf buff) {
    if (sent) {
      throw new IllegalStateException();
    }
    sent = true;
    conn.bilto(new Http2Connection.Http2Stream(headers, buff, headersHandler, dataHandler, resetHandler, endHandler));
  }
}
