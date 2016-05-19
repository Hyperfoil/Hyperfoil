package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.vertx.core.buffer.Buffer;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Stream {

  private final ChannelHandlerContext ctx;
  private final Http2ConnectionEncoder encoder;
  private final Http2Headers headers;
  private boolean sent;
  boolean ended;
  final String method;
  final int id;
  Consumer<HeadersFrame> headersHandler;
  Consumer<DataFrame> dataHandler;
  Consumer<RstFrame> resetHandler;
  Consumer<Void> endHandler;

  public Stream(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int id, String method, String path) {
    this.ctx = ctx;
    this.encoder = encoder;
    this.id = id;
    this.method = method;
    this.headers = Client.headers(method, "https", path);
  }

  public Stream putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  public Stream headersHandler(Consumer<HeadersFrame> handler) {
    headersHandler = handler;
    return this;
  }

  public Stream dataHandler(Consumer<DataFrame> handler) {
    dataHandler = handler;
    return this;
  }

  public Stream resetHandler(Consumer<RstFrame> handler) {
    resetHandler = handler;
    return this;
  }

  public Stream endHandler(Consumer<Void> handler) {
    endHandler = handler;
    return this;
  }

  public void end(ByteBuf buff) {
    if (sent) {
      throw new IllegalStateException();
    }
    sent = true;
    encoder.writeHeaders(ctx, id, headers, 0, buff == null, ctx.newPromise());
    if (buff != null) {
      encoder.writeData(ctx, id, buff, 0, true, ctx.newPromise());
    }
    ctx.flush();
  }

  public void end() {
    end(null);
  }
}
