package http2.bench.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Client {

  static Http2Headers headers(String method, String scheme, String path) {
    return new DefaultHttp2Headers().method(method).scheme(scheme).path(path).authority("localhost:8443");
  }

  static Http2Headers GET(String scheme, String path) {
    return headers("GET", scheme, path);
  }

  static Http2Headers GET(String path) {
    return headers("GET", "https", path);
  }

  static Http2Headers POST(String path) {
    return headers("POST", "https", path);
  }

  final Http2Settings settings = new Http2Settings();
  final EventLoopGroup eventLoopGroup;
  final SslContext sslContext;

  public Client(EventLoopGroup eventLoopGroup, SslContext sslContext) {
    this.eventLoopGroup = eventLoopGroup;
    this.sslContext = sslContext;
  }

  class TestClientHandler extends Http2ConnectionHandler {

    private final BiConsumer<Connection, Throwable> requestHandler;
    private boolean handled;

    public TestClientHandler(
        BiConsumer<Connection, Throwable> requestHandler,
        Http2ConnectionDecoder decoder,
        Http2ConnectionEncoder encoder,
        Http2Settings initialSettings) {
      super(decoder, encoder, initialSettings);
      this.requestHandler = requestHandler;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      super.handlerAdded(ctx);
      if (ctx.channel().isActive()) {
        checkHandle(ctx);
      }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
      checkHandle(ctx);
    }

    private void checkHandle(ChannelHandlerContext ctx) {
      if (!handled) {
        handled = true;
        Connection conn = new Connection(ctx, connection(), encoder(), decoder());
        requestHandler.accept(conn, null);
      }
    }
  }

  class TestClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<TestClientHandler, TestClientHandlerBuilder> {

    private final BiConsumer<Connection, Throwable> requestHandler;

    public TestClientHandlerBuilder(BiConsumer<Connection, Throwable> requestHandler) {
      this.requestHandler = requestHandler;
    }

    @Override
    protected TestClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      return new TestClientHandler(requestHandler, decoder, encoder, initialSettings);
    }

    public TestClientHandler build(Http2Connection conn) {
      connection(conn);
      initialSettings(settings);
      frameListener(new Http2EventAdapter() {
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
          return super.onDataRead(ctx, streamId, data, padding, endOfStream);
        }
      });
      return super.build();
    }
  }

  protected ChannelInitializer channelInitializer(BiConsumer<Connection, Throwable> handler) {
    return new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
        ch.pipeline().addLast(new ApplicationProtocolNegotiationHandler("http/1.1") {
          @Override
          protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
              ChannelPipeline p = ctx.pipeline();
              Http2Connection connection = new DefaultHttp2Connection(false);
              TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder(handler);
              TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
              p.addLast(clientHandler);
              return;
            }
            ctx.close();
            throw new IllegalStateException("unknown protocol: " + protocol);
          }
        });
      }
    };
  }

  public ChannelFuture connect(int port, String host, BiConsumer<Connection, Throwable> handler) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.group(eventLoopGroup);
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.option(ChannelOption.SO_REUSEADDR, true);
    bootstrap.handler(channelInitializer(handler));
    ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
    fut.addListener(v -> {
      if (!v.isSuccess()) {
        handler.accept(null, v.cause());
      }
    });
    return fut;
  }
}
