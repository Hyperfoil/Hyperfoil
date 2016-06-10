package http2.bench.client;

import io.netty.buffer.ByteBuf;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
interface HttpStream {

  HttpStream putHeader(String name, String value);

  HttpStream headersHandler(Consumer<HttpHeaders> handler);

  HttpStream resetHandler(Consumer<RstFrame> handler);

  HttpStream endHandler(Consumer<Void> handler);

  void end(ByteBuf buff);

  default void end() {
    end(null);
  }
}
