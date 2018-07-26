package io.sailrocket.core.client;

import io.netty.buffer.ByteBuf;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpRequest {

  HttpRequest putHeader(String name, String value);

  HttpRequest headersHandler(IntConsumer handler);

  HttpRequest resetHandler(IntConsumer handler);

  HttpRequest endHandler(Consumer<Void> handler);

  void end(ByteBuf buff);

  default void end() {
    end(null);
  }
}
