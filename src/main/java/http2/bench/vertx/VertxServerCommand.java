package http2.bench.vertx;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerCommandBase;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class VertxServerCommand extends ServerCommandBase {

  @Parameter(names = "--internal-blocking-pool-size", description = "Internal blocking pool size", arity = 1)
  public int internalBlockingPoolSize = VertxOptions.DEFAULT_INTERNAL_BLOCKING_POOL_SIZE;

  @Parameter(names = "--open-ssl")
  public boolean openSSL;

  @Parameter(names = "--instances")
  public int instances = 2 * Runtime.getRuntime().availableProcessors();

  public void run() {
    VertxOptions options = createOptions();
    Vertx vertx = Vertx.vertx(options);
    DeploymentOptions deplOptions = new DeploymentOptions().setInstances(instances);
    deplOptions.setConfig(new JsonObject().
        put("clearText", clearText).
        put("port", port).
        put("openSSL", openSSL).
        put("soAcceptBacklog", soBacklog).
        put("poolSize", (int)Math.floor(poolSize / ((double)instances))).
        put("delay", delay).
        put("backendHost", backendHost).
        put("backendPort", backendPort).
        put("backend", backend.name()));
    vertx.deployVerticle(ServerVerticle.class.getName(), deplOptions, ar -> {
      if (ar.succeeded()) {
        System.out.println("Server started");
        startLogMetrics(vertx);
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  private Map<Object, SocketMetric> queuedStreams = new LinkedHashMap<>();
  private AtomicInteger queueRequests = new AtomicInteger();

  private void startLogMetrics(Vertx vertx) {
    vertx.setPeriodic(1000, timerID -> {
      StringBuilder buff = new StringBuilder();
      TreeMap<Integer, AtomicInteger> abc = new TreeMap<>();
      ArrayList<Long> val = new ArrayList<Long>(queuedStreams.size());
      queuedStreams.forEach((conn, stream) -> {
        if (buff.length() > 0) {
          buff.append("\n");
        }
        long l = TimeUnit.NANOSECONDS.toMillis(stream.responseTimes.stream().reduce(0L, (a, b) -> a + b));
        if (stream.responseTimes.size() > 0) {
          l /= stream.responseTimes.size();
        }
        val.add(l);
        abc.computeIfAbsent(stream.pendingResponses.get(), v -> new AtomicInteger()).incrementAndGet();
      });
      abc.forEach((pending, count) -> {
        buff.append(pending).append(": ").append(count.get()).append("\n");
      });
      String log = buff.toString();
      long avg = val.isEmpty() ? 0 : val.stream().reduce(0L, (a, b) -> a+b) / val.size();
      vertx.executeBlocking(fut -> {
        fut.complete();
        System.out.format("pending: %d%n", queueRequests.get());
        System.out.format("avg: %d%n", avg);
        System.out.format(log);
      }, ar -> {

      });
    });
  }

  private VertxOptions createOptions() {
    VertxOptions options = new VertxOptions().setInternalBlockingPoolSize(internalBlockingPoolSize);
    options.setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new VertxMetricsFactory() {
      @Override
      public VertxMetrics metrics(Vertx vertx, VertxOptions options) {
        return new DummyVertxMetrics() {
          @Override
          public HttpClientMetrics<SocketMetric, Void, SocketMetric, Void, Void> createMetrics(HttpClient client, HttpClientOptions options) {
            return new HttpClientMetrics<SocketMetric, Void, SocketMetric, Void, Void>() {

              @Override
              public SocketMetric connected(SocketAddress remoteAddress, String remoteName) {
                SocketMetric socketMetric = new SocketMetric();
                queuedStreams.put(socketMetric, socketMetric);
                return socketMetric;
              }

              @Override
              public SocketMetric requestBegin(Void endpointMetric, SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) {
                socketMetric.startTimes.add(System.nanoTime());
                return socketMetric;
              }

              @Override
              public void requestEnd(SocketMetric socketMetric) {
                queuedStreams.get(socketMetric).pendingResponses.incrementAndGet();
              }

              @Override
              public void responseBegin(SocketMetric socketMetric, HttpClientResponse response) {
                socketMetric.pendingResponses.decrementAndGet();
              }

              public void requestReset(SocketMetric socketMetric) {
                socketMetric.pendingResponses.decrementAndGet();
              }

              @Override
              public void responseEnd(SocketMetric socketMetric, HttpClientResponse response) {
                long startTime = socketMetric.startTimes.remove();
                long elapsed = System.nanoTime() - startTime;
                while (socketMetric.responseTimes.size() > 255) {
                  socketMetric.responseTimes.remove();
                }
                socketMetric.responseTimes.add(elapsed);
                socketMetric.responsesCount.incrementAndGet();
              }

              @Override
              public void disconnected(SocketMetric socketMetric, SocketAddress remoteAddress) {
                queuedStreams.remove(socketMetric);
              }

              public Void enqueueRequest(Void endpointMetric) {
                queueRequests.incrementAndGet();
                return null;
              }

              public void dequeueRequest(Void endpointMetric, Void taskMetric) {
                queueRequests.decrementAndGet();
              }

              public Void createEndpoint(String host, int port, int maxPoolSize) {
                return null;
              }
              public void closeEndpoint(String host, int port, Void endpointMetric) { }
              public void endpointConnected(Void endpointMetric, SocketMetric socketMetric) { }
              public void endpointDisconnected(Void endpointMetric, SocketMetric socketMetric) { }
              public SocketMetric responsePushed(Void endpointMetric, SocketMetric socketMetric, SocketAddress localAddress, SocketAddress remoteAddress, HttpClientRequest request) { return null; }
              public Void connected(Void endpointMetric, SocketMetric socketMetric, WebSocket webSocket) { return null; }
              public void disconnected(Void webSocketMetric) { }
              public void bytesRead(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) { }
              public void bytesWritten(SocketMetric socketMetric, SocketAddress remoteAddress, long numberOfBytes) { }
              public void exceptionOccurred(SocketMetric socketMetric, SocketAddress remoteAddress, Throwable t) { }
              public boolean isEnabled() {
                return true;
              }
              public void close() { }
            };
          }
        };
      }
    }));
    return options;
  }

  static class SocketMetric {
    static volatile int serial = 0;
    final ArrayDeque<Long> startTimes = new ArrayDeque<>(16);
    final ArrayDeque<Long> responseTimes = new ArrayDeque<>(256);
    final AtomicInteger pendingResponses = new AtomicInteger();
    final AtomicInteger responsesCount = new AtomicInteger();
  }
}
