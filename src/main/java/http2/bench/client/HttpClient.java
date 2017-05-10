package http2.bench.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class HttpClient {

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

  public HttpClient(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
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
          synchronized (HttpClient.this) {
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
            synchronized (HttpClient.this) {
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
          synchronized (HttpClient.this) {
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

  abstract long bytesRead();

  abstract long bytesWritten();

  synchronized int count() {
    return all.size();
  }

  synchronized HttpConnection choose() {
    int size = all.size();
    for (int i = 0; i < size; i++) {
      HttpConnection conn = all.get((int) index++ % size);
      if (conn.isAvailable()) {
        return conn;
      }
    }
    return null;
  }

  abstract void resetStatistics();

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
