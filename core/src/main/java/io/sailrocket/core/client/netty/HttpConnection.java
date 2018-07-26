package io.sailrocket.core.client.netty;

import io.sailrocket.core.client.HttpMethod;
import io.sailrocket.core.client.HttpRequest;
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
