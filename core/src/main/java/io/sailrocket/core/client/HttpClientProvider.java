package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientBuilder;
import io.sailrocket.core.client.vertx.VertxHttpClientBuilder;

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
