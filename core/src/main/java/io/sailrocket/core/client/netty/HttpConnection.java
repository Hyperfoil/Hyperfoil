package io.sailrocket.core.client.netty;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
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
