package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Stream implements HttpStream {

  private final ChannelHandlerContext ctx;
  private final Http2ConnectionEncoder encoder;
  private final Http2Headers headers;
  private boolean sent;
  boolean ended;
  final String method;
  final int id;
  Consumer<HttpHeaders> headersHandler;
  Consumer<ByteBuf> dataHandler;
  Consumer<RstFrame> resetHandler;
  Consumer<Void> endHandler;

  public Http2Stream(Http2Client client, ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int id, String method, String path) {
    this.ctx = ctx;
    this.encoder = encoder;
    this.id = id;
    this.method = method;
    this.headers = client.headers(method, "https", path);
  }

  public Http2Stream putHeader(String name, String value) {
    headers.add(name, value);
    return this;
  }

  public Http2Stream headersHandler(Consumer<HttpHeaders> handler) {
    headersHandler = handler;
    return this;
  }

  public Http2Stream dataHandler(Consumer<ByteBuf> handler) {
    dataHandler = handler;
    return this;
  }

  public Http2Stream resetHandler(Consumer<RstFrame> handler) {
    resetHandler = handler;
    return this;
  }

  public Http2Stream endHandler(Consumer<Void> handler) {
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

}
