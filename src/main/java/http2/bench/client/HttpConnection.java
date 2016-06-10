package http2.bench.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
interface HttpConnection {

  void request(String method, String path, Consumer<HttpStream> handler);

  ChannelHandlerContext context();

  boolean isAvailable();

}
