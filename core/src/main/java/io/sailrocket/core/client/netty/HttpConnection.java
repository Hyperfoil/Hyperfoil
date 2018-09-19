package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection {

  int inflight();

  HttpRequest request(HttpMethod method, String path, ByteBuf body);

  ChannelHandlerContext context();

  boolean isAvailable();

}
