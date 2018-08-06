package io.sailrocket.core.client.netty;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
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

  private final Http1XClientPool client;
  // Todo not use concurrent
  private final Deque<HttpStream> inflights = new ConcurrentLinkedDeque<>();
  private ChannelHandlerContext ctx;
  private AtomicInteger size = new AtomicInteger();

  private class HttpStream implements Runnable {

    private final DefaultFullHttpRequest msg;
    private final IntConsumer headersHandler;
    private final IntConsumer resetHandler;
    private final Consumer<io.sailrocket.api.HttpResponse> endHandler;
    private final Consumer<ByteBuf> dataHandler;

    HttpStream(DefaultFullHttpRequest msg, IntConsumer headersHandler, IntConsumer resetHandler,
               Consumer<ByteBuf> dataHandler, Consumer<io.sailrocket.api.HttpResponse> endHandler) {
      this.msg = msg;
      this.headersHandler = headersHandler;
      this.resetHandler = resetHandler;
      this.dataHandler = dataHandler;
      this.endHandler = endHandler;
    }

    @Override
    public void run() {
      ctx.writeAndFlush(msg);
      inflights.add(this);
    }
  }

  Http1xConnection(Http1XClientPool client) {
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
    private Consumer<io.sailrocket.api.HttpResponse> endHandler;
    private Consumer<ByteBuf> dataHandler;

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
    public HttpRequest statusHandler(IntConsumer handler) {
      headersHandler = handler;
      return this;
    }

    @Override
    public HttpRequest headerHandler(Consumer<Map<String, String>> handler) {
      //TODO
      return this;
    }

    @Override
    public HttpRequest resetHandler(IntConsumer handler) {
      resetHandler = handler;
      return this;
    }

    @Override
    public HttpRequest bodyHandler(Consumer<byte[]> handler) {
      dataHandler = (dataHandler -> handler.accept(dataHandler.array()));
      return null;
    }

    @Override
    public HttpRequest endHandler(Consumer<io.sailrocket.api.HttpResponse> handler) {
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
      msg.headers().add("Host", client.host + ":" + client.port);
      ctx.executor().execute(new HttpStream(msg, headersHandler, resetHandler, dataHandler, endHandler));
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
