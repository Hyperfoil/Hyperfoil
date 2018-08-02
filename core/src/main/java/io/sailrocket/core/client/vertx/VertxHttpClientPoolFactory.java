package io.sailrocket.core.client.vertx;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpVersion;

public class VertxHttpClientPoolFactory implements HttpClientPoolFactory {

  volatile Vertx vertx;
  volatile int threadCount;
  volatile boolean ssl;
  volatile HttpVersion protocol;
  volatile int size;
  volatile int port;
  volatile String host;
  volatile int concurrency;

  @Override
  public HttpClientPoolFactory threads(int count) {
    if (vertx != null) {
      throw new IllegalStateException();
    }
    threadCount = count;
    vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(count));
    return this;
  }

  @Override
  public HttpClientPoolFactory ssl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  @Override
  public HttpClientPoolFactory protocol(HttpVersion protocol) {
    this.protocol = protocol;
    return this;
  }

  @Override
  public HttpClientPoolFactory size(int size) {
    this.size = size;
    return this;
  }

  @Override
  public HttpClientPoolFactory port(int port) {
    this.port = port;
    return this;
  }

  @Override
  public HttpClientPoolFactory host(String host) {
    this.host = host;
    return this;
  }

  @Override
  public HttpClientPoolFactory concurrency(int maxConcurrency) {
    this.concurrency = maxConcurrency;
    return this;
  }

  @Override
  public HttpClientPool build() {
    return new VertxHttpClientPool(this);
  }

  @Override
  public void shutdown() {
    vertx.close();
  }
}
