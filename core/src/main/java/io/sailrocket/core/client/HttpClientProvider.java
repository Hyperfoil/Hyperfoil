package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientPoolFactory;
import io.sailrocket.spi.HttpClientPoolFactory;

public enum HttpClientProvider {

  netty() {
    @Override
    public HttpClientPoolFactory builder() {
      return new NettyHttpClientPoolFactory();
    }
  };

  public abstract HttpClientPoolFactory builder();

}
