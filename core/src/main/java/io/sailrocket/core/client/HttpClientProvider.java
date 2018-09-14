package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientPoolFactory;
import io.sailrocket.core.client.vertx.VertxHttpClientPoolFactory;
import io.sailrocket.spi.HttpClientPoolFactory;

public enum HttpClientProvider {

  @Deprecated
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
