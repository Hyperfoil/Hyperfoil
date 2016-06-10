package http2.bench.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Client extends HttpClient {

  Http2Headers headers(String method, String scheme, String path) {
    return new DefaultHttp2Headers().method(method).scheme(scheme).path(path).authority(authority);
  }

  Http2Headers GET(String scheme, String path) {
    return headers("GET", scheme, path);
  }

  Http2Headers GET(String path) {
    return headers("GET", "https", path);
  }

  Http2Headers POST(String path) {
    return headers("POST", "https", path);
  }

  final Http2Settings settings = new Http2Settings();
  private final String authority;
  private final StatisticsHandler statisticsHandler = new StatisticsHandler();

  public Http2Client(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
    super(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
    this.authority = host + ":" + port;
  }

  class TestClientHandler extends Http2ConnectionHandler {

    private final BiConsumer<HttpConnection, Throwable> requestHandler;
    private boolean handled;

    public TestClientHandler(
        BiConsumer<HttpConnection, Throwable> requestHandler,
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
        Http2Connection conn = new Http2Connection(ctx, connection(), encoder(), decoder(), Http2Client.this);
        // Use a very large stream window size
        conn.incrementConnectionWindowSize(1073676288 - 65535);
        requestHandler.accept(conn, null);
      }
    }
  }

  class TestClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<TestClientHandler, TestClientHandlerBuilder> {

    private final BiConsumer<HttpConnection, Throwable> requestHandler;

    public TestClientHandlerBuilder(BiConsumer<HttpConnection, Throwable> requestHandler) {
      this.requestHandler = requestHandler;
    }

    @Override
    protected TestClientHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) throws Exception {
      return new TestClientHandler(requestHandler, decoder, encoder, initialSettings);
    }

    public TestClientHandler build(io.netty.handler.codec.http2.Http2Connection conn) {
      connection(conn);
      initialSettings(settings);
      frameListener(new Http2EventAdapter() { /* Dunno why this is needed */ });
      return super.build();
    }
  }

  protected ChannelInitializer channelInitializer(BiConsumer<HttpConnection, Throwable> handler) {
    return new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        TestClientHandlerBuilder clientHandlerBuilder = new TestClientHandlerBuilder(handler);
        if (sslContext != null) {
          pipeline.addLast(sslContext.newHandler(ch.alloc()));
          pipeline.addLast(statisticsHandler);
          pipeline.addLast(new ApplicationProtocolNegotiationHandler("http/1.1") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
              if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ChannelPipeline p = ctx.pipeline();
                io.netty.handler.codec.http2.Http2Connection connection = new DefaultHttp2Connection(false);
                TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
                p.addLast(clientHandler);
                return;
              }
              ctx.close();
              throw new IllegalStateException("unknown protocol: " + protocol);
            }
          });
        } else {
          pipeline.addLast(statisticsHandler);
          io.netty.handler.codec.http2.Http2Connection connection = new DefaultHttp2Connection(false);
          TestClientHandler clientHandler = clientHandlerBuilder.build(connection);
          HttpClientCodec sourceCodec = new HttpClientCodec();
          Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(clientHandler);
          HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);
          ChannelHandler upgradeRequestHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
              DefaultFullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
              ctx.writeAndFlush(upgradeRequest);
              ctx.fireChannelActive();
              ctx.pipeline().remove(this);
            }
          };
          pipeline.addLast(sourceCodec,
              upgradeHandler,
              upgradeRequestHandler);
        }
      }
    };
  }



  public void connect(int port, String host, BiConsumer<HttpConnection, Throwable> handler) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.group(eventLoopGroup);
//    bootstrap.option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator());
    bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
    bootstrap.option(ChannelOption.SO_REUSEADDR, true);
    bootstrap.handler(channelInitializer(handler));
    ChannelFuture fut = bootstrap.connect(new InetSocketAddress(host, port));
    fut.addListener(v -> {
      if (!v.isSuccess()) {
        handler.accept(null, v.cause());
      }
    });
  }

  public long bytesRead() {
    return statisticsHandler.bytesRead();
  }

  public long bytesWritten() {
    return statisticsHandler.bytesWritten();
  }

  public void resetStatistics() {
    statisticsHandler.reset();
  }
}
