package http2.bench.client;

import http2.bench.client.HttpClientBuilder;
import http2.bench.client.netty.NettyHttpClientBuilder;
import http2.bench.client.vertx.VertxHttpClientBuilder;

public enum HttpClientProvider {

  vertx() {
    @Override
    public HttpClientBuilder builder() {
      return new VertxHttpClientBuilder();
    }
  },

  netty() {
    @Override
    public HttpClientBuilder builder() {
      return new NettyHttpClientBuilder();
    }
  };

  public abstract HttpClientBuilder builder();

}
