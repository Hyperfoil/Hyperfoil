package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientPool;
import io.sailrocket.core.client.vertx.VertxHttpClientPool;

public enum HttpClientProvider {

  vertx() {
    @Override
    public HttpClientPool builder() {
      return new VertxHttpClientPool();
    }
  },

  netty() {
    @Override
    public HttpClientPool builder() {
      return new NettyHttpClientPool();
    }
  };

  public abstract HttpClientPool builder();

}
