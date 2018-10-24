package io.sailrocket.core.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.sailrocket.api.connection.HttpConnectionPool;

import java.net.InetSocketAddress;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1XClientPool extends HttpClientPoolImpl {

  Http1XClientPool(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
    super(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
  }

  @Override
  void connect(HttpConnectionPool pool, BiConsumer<HttpConnection, Throwable> handler) {

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.group(eventLoopGroup);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.option(ChannelOption.SO_REUSEADDR, true);

    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslContext != null) {
          SslHandler sslHandler = sslContext.newHandler(ch.alloc(), host, port);
          ch.pipeline().addLast(sslHandler);
        }
        Http1xConnection connection = new Http1xConnection(Http1XClientPool.this, pool);
        pipeline.addLast(new Http1xRawBytesHandler(connection));
        pipeline.addLast("codec", new HttpClientCodec(4096, 8192, 8192, false, false));
        pipeline.addLast("handler", connection);
      }
    });

    ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
    fut.addListener(v -> {
      if (v.isSuccess()) {
        handler.accept(fut.channel().pipeline().get(Http1xConnection.class), null);
      } else {
        handler.accept(null, v.cause());
      }
    });
  }
}
