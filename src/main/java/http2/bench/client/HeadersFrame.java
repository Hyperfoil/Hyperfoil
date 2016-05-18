package http2.bench.client;

import io.netty.handler.codec.http2.Http2Headers;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HeadersFrame {

  final Http2Headers headers;
  final boolean end;

  public HeadersFrame(Http2Headers headers, boolean end) {
    this.headers = headers;
    this.end = end;
  }
}
