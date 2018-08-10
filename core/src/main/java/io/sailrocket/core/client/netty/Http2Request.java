package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.client.AbstractHttpRequest;
import io.sailrocket.spi.HttpHeader;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Request extends AbstractHttpRequest {

  private final Http2Connection conn;
  private final Http2Headers headers;
  private boolean sent;
  final HttpMethod method;

  Http2Request(Http2ClientPool client, Http2Connection conn, HttpMethod method, String path) {
    this.method = method;
    this.conn = conn;
    this.headers = client.headers(method.name(), "https", path);
  }

  public Http2Request putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  public void end(ByteBuf buff) {
    if (sent) {
      throw new IllegalStateException();
    }
    sent = true;
    conn.bilto(new Http2Connection.Http2Stream(headers, buff, statusHandler, byteBuf -> dataHandler.accept(byteBuf.array()), resetHandler, endHandler));
  }
}
