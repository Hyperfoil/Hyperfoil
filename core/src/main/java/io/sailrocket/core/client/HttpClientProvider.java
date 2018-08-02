package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientPoolFactory;
import io.sailrocket.core.client.vertx.VertxHttpClientPoolFactory;

public enum HttpClientProvider {

  vertx() {
    @Override
    public HttpClientPoolFactory builder() {
      return new VertxHttpClientPoolFactory();
    }
  },

  netty() {
    @Override
    public HttpClientPoolFactory builder() {
      return new NettyHttpClientPoolFactory();
    }
  };

  public abstract HttpClientPoolFactory builder();

}
