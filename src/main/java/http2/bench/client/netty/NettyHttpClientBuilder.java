package http2.bench.client.netty;

import http2.bench.client.HttpClient;
import http2.bench.client.HttpClientBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.core.http.HttpVersion;

import java.util.concurrent.TimeUnit;

public class NettyHttpClientBuilder implements HttpClientBuilder {

  private EventLoopGroup workerGroup = new NioEventLoopGroup();;
  private volatile HttpVersion protocol;
  private volatile int size;
  private volatile boolean ssl;
  private volatile int port;
  private volatile String host;
  private volatile int concurrency;

  @Override
  public HttpClientBuilder ssl(boolean ssl) {
    this.ssl = ssl;
    return this;
  }

  public HttpClientBuilder protocol(HttpVersion protocol) {
    this.protocol = protocol;
    return this;
  }

  public HttpClientBuilder size(int size) {
    this.size = size;
    return this;
  }

  public HttpClientBuilder port(int port) {
    this.port = port;
    return this;
  }

  public HttpClientBuilder host(String host) {
    this.host = host;
    return this;
  }

  public HttpClientBuilder concurrency(int maxConcurrency) {
    this.concurrency = maxConcurrency;
    return this;
  }

  @Override
  public HttpClient build() throws Exception {
    return HttpClientImpl.create(workerGroup, protocol, ssl, size, port, host, concurrency);
  }

  @Override
  public void shutdown() {
    workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
  }
}
