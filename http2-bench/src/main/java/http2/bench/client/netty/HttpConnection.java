package http2.bench.client.netty;

import http2.bench.client.HttpMethod;
import http2.bench.client.HttpRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection {

  int inflight();

  HttpRequest request(HttpMethod method, String path);

  ChannelHandlerContext context();

  boolean isAvailable();

}
