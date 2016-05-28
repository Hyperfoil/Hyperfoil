package http2.bench.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
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
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Client {

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

  private final int size;
  private final int port;
  private final String host;
  private final String authority;
  final Http2Settings settings = new Http2Settings();
  final EventLoopGroup eventLoopGroup;
  final SslContext sslContext;
  private final ArrayList<Connection> all = new ArrayList<>();
  private int index;
  private int count; // The estimated count : created + creating
  private final EventLoop scheduler;
  private boolean shutdown;
  private Consumer<Void> startedHandler;
  private final LongAdder bytesRead = new LongAdder();
  private final LongAdder bytesWritten = new LongAdder();

  public Client(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host) {
    this.eventLoopGroup = eventLoopGroup;
    this.sslContext = sslContext;
    this.size = size;
    this.port = port;
    this.host = host;
    this.scheduler = eventLoopGroup.next();
    this.authority = host + ":" + port;
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
        Connection conn = new Connection(ctx, connection(), encoder(), decoder(), Client.this);
        synchronized (Client.this) {
          all.add(conn);
        }
        ctx.channel().closeFuture().addListener(v -> {
          synchronized (Client.this) {
            count--;
            all.remove(conn);
          }
          if (!shutdown) {
            checkCreateConnections(0);
          }
        });

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
      frameListener(new Http2EventAdapter() { /* Dunno why this is needed */ });
      return super.build();
    }
  }

  protected ChannelInitializer channelInitializer(BiConsumer<Connection, Throwable> handler) {
    return new ChannelInitializer<Channel>() {
      private int sizeOf(Object msg) {
        if (msg instanceof ByteBuf) {
          return ((ByteBuf) msg).readableBytes();
        } else if (msg instanceof ByteBufHolder) {
          return ((ByteBufHolder) msg).content().readableBytes();
        } else {
          return 0;
        }
      }
      @Override
      protected void initChannel(Channel ch) throws Exception {
        ch.pipeline().addLast(new ChannelDuplexHandler() {
          @Override
          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            int size = sizeOf(msg);
            if (size > 0) {
              bytesRead.add(size);
            }
            super.channelRead(ctx, msg);
          }
          @Override
          public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            int size = sizeOf(msg);
            if (size > 0) {
              bytesWritten.add(size);
            }
            super.write(ctx, msg, promise);
          }
        });
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

  public void start(Consumer<Void> completionHandler) {
    synchronized (this) {
      if (startedHandler != null) {
        throw new IllegalStateException();
      }
      startedHandler = completionHandler;
    }
    checkCreateConnections(0);
  }

  private synchronized void checkCreateConnections(int retry) {
    if (retry > 100) {
      System.out.println("DANGER - handle me");
    }
    if (count < size) {
      count++;
      connect(port, host, (conn, err) -> {
        if (err == null) {
          Consumer<Void> handler = null;
          synchronized (Client.this) {
            if (count < size) {
              checkCreateConnections(0);
            } else {
              if (count() == size) {
                handler = startedHandler;
                startedHandler = null;
              }
            }
          }
          if (handler != null) {
            handler.accept(null);
          }
        } else {
          synchronized (Client.this) {
            count--;
          }
          checkCreateConnections(retry + 1);
        }
      });
      scheduler.schedule(() -> {
        checkCreateConnections(retry);
      }, 2, TimeUnit.MILLISECONDS);

    }
  }


  public ChannelFuture connect(int port, String host, BiConsumer<Connection, Throwable> handler) {
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
    return fut;
  }

  public synchronized Connection choose(int maxConcurrentStream) {
    int size = all.size();
    for (int i = 0; i < size; i++) {
      index %= size;
      Connection conn = all.get(index++);
      if (conn.numActiveStreams() < maxConcurrentStream) {
        return conn;
      }
    }
    return null;
  }

  public synchronized int count() {
    return all.size();
  }

  public long bytesRead() {
    return bytesRead.longValue();
  }

  public long bytesWritten() {
    return bytesWritten.longValue();
  }

  public void resetStatistics() {
    bytesRead.reset();
    bytesWritten.reset();
  }

  public void shutdown() {
    HashSet<Connection> list;
    synchronized (this) {
      if (!shutdown) {
        shutdown = true;
      } else {
        return;
      }
      list = new HashSet<>(all);
    }
    for (Connection conn : list) {
      ChannelHandlerContext ctx = conn.context;
      ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
      ctx.close();
      ctx.flush();
    }
  }
}
