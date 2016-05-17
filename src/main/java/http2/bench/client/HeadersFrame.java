package http2.bench.client;

import io.netty.handler.codec.Headers;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HeadersFrame {

  final Headers headers;
  final boolean end;

  public HeadersFrame(Headers headers, boolean end) {
    this.headers = headers;
    this.end = end;
  }
}
