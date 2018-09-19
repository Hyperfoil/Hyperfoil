package io.sailrocket.core.client;


import io.sailrocket.core.client.netty.NettyHttpClientPoolFactory;
import io.sailrocket.api.connection.HttpClientPoolFactory;

public enum HttpClientProvider {

  netty() {
    @Override
    public HttpClientPoolFactory builder() {
      return new NettyHttpClientPoolFactory();
    }
  };

  public abstract HttpClientPoolFactory builder();

}
