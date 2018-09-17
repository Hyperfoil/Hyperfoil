package io.sailrocket.core.client.netty;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.spi.HttpClientPoolFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.core.http.HttpVersion;

public class NettyHttpClientPoolFactory implements HttpClientPoolFactory {

  private int threads;
  private HttpVersion protocol;
  private int size;
  private boolean ssl;
  private int port;
  private String host;
  private int concurrency;

  @Override
  public HttpClientPoolFactory threads(int count) {
    this.threads = count;
    return this;
  }

  @Override
  public HttpClientPoolFactory ssl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public HttpClientPoolFactory protocol(HttpVersion protocol) {
    this.protocol = protocol;
    return this;
  }

  public HttpClientPoolFactory size(int size) {
    this.size = size;
    return this;
  }

  public HttpClientPoolFactory port(int port) {
    this.port = port;
    return this;
  }

  public HttpClientPoolFactory host(String host) {
    this.host = host;
    return this;
  }

  public HttpClientPoolFactory concurrency(int maxConcurrency) {
    this.concurrency = maxConcurrency;
    return this;
  }

  @Override
  public HttpClientPool build() throws Exception {
    EventLoopGroup workerGroup = new NioEventLoopGroup(this.threads);
    return HttpClientPoolImpl.create(workerGroup, protocol, ssl, size, port, host, concurrency);
  }
}
