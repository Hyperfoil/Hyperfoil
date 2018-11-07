package io.sailrocket.core.client.netty;

import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.connection.HttpClientPoolFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.sailrocket.api.http.HttpVersion;

public class NettyHttpClientPoolFactory implements HttpClientPoolFactory {

  private int threads;
  private HttpVersion[] versions;
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

  public HttpClientPoolFactory versions(HttpVersion[] versions) {
    this.versions = versions;
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
    return new HttpClientPoolImpl(workerGroup, versions, ssl, size, port, host, concurrency);
  }
}
