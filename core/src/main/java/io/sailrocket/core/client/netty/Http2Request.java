package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.core.client.AbstractHttpRequest;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Request extends AbstractHttpRequest {

  private final Http2Connection conn;
  private final Http2Headers headers;
  private final ByteBuf body;
  private boolean sent;

  Http2Request(HttpClientPoolImpl client, Http2Connection conn, HttpMethod method, String path, ByteBuf buf) {
    super(method);
    this.conn = conn;
    this.headers = new DefaultHttp2Headers().method(method.name()).scheme(client.scheme).path(path).authority(client.authority);
    this.body = buf;
  }

  public Http2Request putHeader(CharSequence name, CharSequence value) {
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

   @Override
   public Connection connection() {
      return conn;
   }
}
