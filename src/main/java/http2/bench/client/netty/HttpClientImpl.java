package http2.bench.client.netty;

import http2.bench.client.HttpClient;
import http2.bench.client.HttpMethod;
import http2.bench.client.HttpRequest;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.core.http.HttpVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class HttpClientImpl implements HttpClient {

  static HttpClient create(EventLoopGroup eventLoopGroup, HttpVersion protocol, boolean ssl, int size, int port, String host, int maxConcurrentStream) throws Exception {
    SslContext sslContext = null;
    if (ssl) {
      SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
      SslContextBuilder builder = SslContextBuilder.forClient()
          .sslProvider(provider)
          /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
           * Please refer to the HTTP/2 specification for cipher requirements. */
          .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
          .trustManager(InsecureTrustManagerFactory.INSTANCE);
      if (protocol == HttpVersion.HTTP_2) {
        builder.applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_2,
            ApplicationProtocolNames.HTTP_1_1));
      }
      sslContext = builder
          .build();
    }
    if (protocol == HttpVersion.HTTP_2) {
      return new Http2Client(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
    } else {
      return new Http1xClient(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
    }
  }

  final int maxConcurrentStream;
  private final int size;
  private final int port;
  private final String host;
  final EventLoopGroup eventLoopGroup;
  private final EventLoop scheduler;
  final SslContext sslContext;
  private final ArrayList<HttpConnection> all = new ArrayList<>();
  private long index;
  private int count; // The estimated count : created + creating
  private Consumer<Void> startedHandler;
  private boolean shutdown;

  HttpClientImpl(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
    this.maxConcurrentStream = maxConcurrentStream;
    this.eventLoopGroup = eventLoopGroup;
    this.sslContext = sslContext;
    this.size = size;
    this.port = port;
    this.host = host;
    this.scheduler = eventLoopGroup.next();
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
          synchronized (HttpClientImpl.this) {
            all.add(conn);
            if (count < size) {
              checkCreateConnections(0);
            } else {
              if (count() == size) {
                handler = startedHandler;
                startedHandler = null;
              }
            }
          }

          conn.context().channel().closeFuture().addListener(v -> {
            synchronized (HttpClientImpl.this) {
              count--;
              all.remove(conn);
            }
            if (!shutdown) {
              checkCreateConnections(0);
            }
          });

          if (handler != null) {
            handler.accept(null);
          }
        } else {
          synchronized (HttpClientImpl.this) {
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

  abstract void connect(int port, String host, BiConsumer<HttpConnection, Throwable> handler);

  public abstract long bytesRead();

  public abstract long bytesWritten();

  synchronized int count() {
    return all.size();
  }

  @Override
  public HttpRequest request(HttpMethod method, String path) {
    HttpConnection conn = choose();
    if (conn == null) {
      return null;
    }
    return conn.request(method, path);
  }

  private synchronized HttpConnection choose() {
    int size = all.size();
    for (int i = 0; i < size; i++) {
      HttpConnection conn = all.get((int) index++ % size);
      if (conn.isAvailable()) {
        return conn;
      }
    }
    return null;
  }

  public abstract void resetStatistics();

  @Override
  public synchronized long inflight() {
    long inflight = 0;
    for (HttpConnection conn : all) {
      inflight += conn.inflight();
    }
    return inflight;
  }

  public void shutdown() {
    HashSet<HttpConnection> list;
    synchronized (this) {
      if (!shutdown) {
        shutdown = true;
      } else {
        return;
      }
      list = new HashSet<>(all);
    }
    for (HttpConnection conn : list) {
      ChannelHandlerContext ctx = conn.context();
      ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
      ctx.close();
      ctx.flush();
    }
  }

}
