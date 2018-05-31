package http2.bench.client.netty;

import http2.bench.client.HttpMethod;
import http2.bench.client.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {

  private final Http1xClient client;
  // Todo not use concurrent
  private final Deque<HttpStream> inflights = new ConcurrentLinkedDeque<>();
  private ChannelHandlerContext ctx;
  private AtomicInteger size = new AtomicInteger();

  private class HttpStream implements Runnable {

    private final DefaultFullHttpRequest msg;
    private final IntConsumer headersHandler;
    private final IntConsumer resetHandler;
    private final Consumer<Void> endHandler;

    HttpStream(DefaultFullHttpRequest msg, IntConsumer headersHandler, IntConsumer resetHandler, Consumer<Void> endHandler) {
      this.msg = msg;
      this.headersHandler = headersHandler;
      this.resetHandler = resetHandler;
      this.endHandler = endHandler;
    }

    @Override
    public void run() {
      ctx.writeAndFlush(msg);
      inflights.add(this);
    }
  }

  Http1xConnection(Http1xClient client) {
    this.client = client;
  }

  @Override
  public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
    this.ctx = ctx;
    super.channelRegistered(ctx);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) msg;
      HttpStream request = inflights.peek();
      if (request.headersHandler != null) {
        request.headersHandler.accept(response.status().code());
      }
    }
    if (msg instanceof LastHttpContent) {
      size.decrementAndGet();
      HttpStream request = inflights.poll();
      if (request.endHandler != null) {
        request.endHandler.accept(null);
      }
    }
    super.channelRead(ctx, msg);
  }

  private class HttpRequestImpl implements HttpRequest {

    private final HttpMethod method;
    private final String path;
    private Map<String, String> headers;
    private IntConsumer headersHandler;
    private IntConsumer resetHandler;
    private Consumer<Void> endHandler;

    HttpRequestImpl(HttpMethod method, String path) {
      this.method = method;
      this.path = path;
      this.headers = new HashMap<>();
    }

    @Override
    public HttpRequest putHeader(String name, String value) {
      headers.put(name, value);
      return this;
    }

    @Override
    public HttpRequest headersHandler(IntConsumer handler) {
      headersHandler = handler;
      return this;
    }

    @Override
    public HttpRequest resetHandler(IntConsumer handler) {
      resetHandler = handler;
      return this;
    }

    @Override
    public HttpRequest endHandler(Consumer<Void> handler) {
      endHandler = handler;
      return this;
    }

    @Override
    public void end(ByteBuf buff) {
      if (buff == null) {
        buff = Unpooled.EMPTY_BUFFER;
      }
      DefaultFullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method.netty, path, buff, false);
      headers.forEach(msg.headers()::add);
      ctx.executor().execute(new HttpStream(msg, headersHandler, resetHandler, endHandler));
    }
  }

  @Override
  public HttpRequest request(HttpMethod method, String path) {
    HttpRequestImpl request = new HttpRequestImpl(method, path);
    size.incrementAndGet();
    return request;
  }

  @Override
  public ChannelHandlerContext context() {
    return ctx;
  }

  @Override
  public boolean isAvailable() {
    return size.get() < client.maxConcurrentStream;
  }

  @Override
  public int inflight() {
    return size.get();
  }
}
