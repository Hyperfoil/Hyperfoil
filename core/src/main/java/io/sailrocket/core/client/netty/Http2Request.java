package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.client.AbstractHttpRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Request extends AbstractHttpRequest {

  private final Http2Connection conn;
  private final Http2Headers headers;
  private final ByteBuf body;
  private boolean sent;

  Http2Request(Http2ClientPool client, Http2Connection conn, HttpMethod method, String path, ByteBuf buf) {
    this.conn = conn;
    this.headers = client.headers(method.name(), "https", path);
    this.body = buf;
  }

  public Http2Request putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  public void end() {
    if (sent) {
      throw new IllegalStateException();
    }
    sent = true;
    conn.bilto(new Http2Connection.Http2Stream(headers, body, this));
  }
}
