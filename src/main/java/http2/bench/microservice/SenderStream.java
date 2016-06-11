package http2.bench.microservice;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SenderStream implements ReadStream<Buffer> {

  private final int chunkSize;
  private final Buffer data;
  private final long length;
  private final Context context = Vertx.currentContext();
  private boolean paused;
  private Handler<Buffer> dataHandler;
  private long sent = 0;
  private Handler<Void> endHandler;

  public SenderStream(long length, int chunkSize) {
    this.chunkSize = chunkSize;
    this.data = Buffer.buffer(new byte[chunkSize]);
    this.length = length;
  }

  @Override
  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public ReadStream<Buffer> handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  public void send() {
    if (!paused) {
      if (sent < length) {
        if (sent >= chunkSize) {
          sent -= chunkSize;
          dataHandler.handle(data);
        } else {
          dataHandler.handle(Buffer.buffer(new byte[(int)(length - sent)]));
          sent = length;
        }
        context.runOnContext(v -> {
          send();
        });
      } else {
        if (endHandler != null) {
          endHandler.handle(null);
        }
      }
    }
  }

  @Override
  public ReadStream<Buffer> pause() {
    paused = true;
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    paused = false;
    send();
    return this;
  }

  @Override
  public ReadStream<Buffer> endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
}
