package io.sailrocket.core.client.vertx;

import io.sailrocket.api.HttpClient;
import io.sailrocket.core.client.HttpClientPool;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpVersion;

public class VertxHttpClientPool implements HttpClientPool {

  volatile Vertx vertx;
  volatile int threadCount;
  volatile boolean ssl;
  volatile HttpVersion protocol;
  volatile int size;
  volatile int port;
  volatile String host;
  volatile int concurrency;

  @Override
  public HttpClientPool threads(int count) {
    if (vertx != null) {
      throw new IllegalStateException();
    }
    threadCount = count;
    vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(count));
    return this;
  }

  @Override
  public HttpClientPool ssl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  @Override
  public HttpClientPool protocol(HttpVersion protocol) {
    this.protocol = protocol;
    return this;
  }

  @Override
  public HttpClientPool size(int size) {
    this.size = size;
    return this;
  }

  @Override
  public HttpClientPool port(int port) {
    this.port = port;
    return this;
  }

  @Override
  public HttpClientPool host(String host) {
    this.host = host;
    return this;
  }

  @Override
  public HttpClientPool concurrency(int maxConcurrency) {
    this.concurrency = maxConcurrency;
    return this;
  }

  @Override
  public HttpClient build() {
    return new VertxHttpClient(this);
  }

  @Override
  public void shutdown() {
    vertx.close();
  }
}
