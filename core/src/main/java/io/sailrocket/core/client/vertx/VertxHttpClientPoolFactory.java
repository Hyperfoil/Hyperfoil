package io.sailrocket.core.client.vertx;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.spi.HttpClientPoolFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpVersion;

@Deprecated
public class VertxHttpClientPoolFactory implements HttpClientPoolFactory {

  int threadCount;
  boolean ssl;
  HttpVersion protocol;
  int size;
  int port;
  String host;
  int concurrency;

  @Override
  public HttpClientPoolFactory threads(int count) {
    threadCount = count;
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
    Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(threadCount));
    return new VertxHttpClientPool(vertx, this.concurrency, this.size, this.threadCount, this.port, this.host, this.ssl);
  }
}
