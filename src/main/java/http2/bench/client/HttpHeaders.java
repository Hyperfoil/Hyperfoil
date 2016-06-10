package http2.bench.client;

import io.netty.handler.codec.http2.Http2Headers;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpHeaders {

  final int status;

  public HttpHeaders(Http2Headers headers) {
    int s = -1;
    try {
      s = (Integer.parseInt(headers.status().toString()) - 200) / 100;
    } catch (NumberFormatException ignore) {
    }
    status = s;
  }

  int status() {
    return status;
  }
}
