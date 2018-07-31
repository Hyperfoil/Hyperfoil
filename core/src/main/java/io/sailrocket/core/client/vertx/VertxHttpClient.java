package io.sailrocket.core.client.vertx;

import io.sailrocket.api.HttpClient;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class VertxHttpClient implements HttpClient {

  private class Slot {

    final Context context = vertx.getOrCreateContext();
    final io.vertx.core.http.HttpClient client;

    Slot(io.vertx.core.http.HttpClient client) {
      this.client = client;
    }
  }

  private final Vertx vertx;
  private final AtomicInteger inflight = new AtomicInteger();
  private final int maxInflight;
  private AtomicInteger currentSlot = new AtomicInteger();
  private Slot[] slots;
  private final ThreadLocal<Slot> current = ThreadLocal.withInitial(() -> slots[currentSlot.getAndIncrement() % slots.length]);

  public VertxHttpClient(VertxHttpClientBuilder builder) {

    HttpClientOptions options = new HttpClientOptions()
        .setSsl(builder.ssl)
        .setTrustAll(true)
        .setVerifyHost(false)
        .setKeepAlive(true)
        .setPipeliningLimit(builder.concurrency)
        .setPipelining(true)
        .setDefaultPort(builder.port)
        .setDefaultHost(builder.host);

    this.vertx = builder.vertx;
    this.maxInflight = builder.concurrency * builder.size;
    this.slots = new Slot[builder.threadCount];

    int perSlotSize = builder.size / slots.length;
    for (int i = 0;i < slots.length;i++) {
      int n = perSlotSize;
      if (i == 0) {
        n += builder.size % slots.length;
      }
      slots[i] = new Slot(vertx.createHttpClient(new HttpClientOptions(options).setMaxPoolSize(n)));
    }
  }

  @Override
  public long inflight() {
    return inflight.get();
  }

  @Override
  public void start(Consumer<Void> completionHandler) {
    completionHandler.accept(null);
  }

  private class VertxHttpRequest implements HttpRequest {

    private Map<String, String> headers;
    private final HttpMethod method;
    private final String path;
    private IntConsumer headersHandler;
    private Consumer<ByteBuf> dataHandler;
    private IntConsumer resetHandler;
    private Consumer<Void> endHandler;

    VertxHttpRequest(HttpMethod method, String path) {
      this.method = method;
      this.path = path;
    }
    @Override
    public HttpRequest putHeader(String name, String value) {
      if (headers == null) {
        headers = new HashMap<>();
      }
      headers.put(name, value);
      return this;
    }
    @Override
    public HttpRequest headersHandler(IntConsumer handler) {
      headersHandler = handler;
      return this;
    }
    @Override
    public HttpRequest resetHandler(IntConsumer handler) {
      resetHandler = handler;
      return this;
    }
    @Override
    public HttpRequest endHandler(Consumer<Void> handler) {
      endHandler = handler;
      return this;
    }
    @Override
    public void end(ByteBuf buff) {
      Slot slot = current.get();
      slot.context.runOnContext(v -> {
        HttpClientRequest request = slot.client.request(method.vertx, path);
        Future<HttpClientResponse> fut = Future.future();
        Future<Void> doneHandler = Future.future();
        doneHandler.setHandler(ar -> {
          inflight.decrementAndGet();
          if (ar.succeeded()) {
            endHandler.accept(null);
          }
        });
        fut.setHandler(ar -> {
          if (ar.succeeded()) {
            HttpClientResponse resp = ar.result();
            headersHandler.accept(resp.statusCode());
            resp.exceptionHandler(fut::tryFail);
            resp.endHandler(doneHandler::tryComplete);
            Consumer<ByteBuf> handler = this.dataHandler;
            if (handler != null) {
              resp.handler(chunk -> handler.accept(chunk.getByteBuf()));
            }
          } else {
            doneHandler.fail(ar.cause());
          }
        });
        request.handler(fut::tryComplete);
        request.exceptionHandler(fut::tryFail);
        if (buff != null) {
          request.end(Buffer.buffer(buff));
        } else {
          request.end();
        }
      });
    }
  }

  @Override
  public HttpRequest request(HttpMethod method, String path) {
    if (inflight.get() < maxInflight) {
      inflight.incrementAndGet();
      return new VertxHttpRequest(method, path);
    }
    return null;
  }

  @Override
  public long bytesRead() {
    return 0;
  }

  @Override
  public long bytesWritten() {
    return 0;
  }

  @Override
  public void resetStatistics() {
  }

  @Override
  public void shutdown() {
  }
}
