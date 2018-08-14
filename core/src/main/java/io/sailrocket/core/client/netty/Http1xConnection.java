package io.sailrocket.core.client.netty;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {

  final Http1XClientPool client;
  // Todo not use concurrent
  private final Deque<HttpStream> inflights = new ConcurrentLinkedDeque<>();
  ChannelHandlerContext ctx;
  private AtomicInteger size = new AtomicInteger();

  HttpStream createStream(DefaultFullHttpRequest msg, IntConsumer headersHandler, IntConsumer resetHandler,
                          Consumer<ByteBuf> dataHandler, Consumer<io.sailrocket.api.HttpResponse> endHandler) {
      return new HttpStream(msg, headersHandler, resetHandler, dataHandler, endHandler);
  }

  class HttpStream implements Runnable {

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

  @Override
  public HttpRequest request(HttpMethod method, String path, ByteBuf body) {
    Http1xRequest request = new Http1xRequest(this, method, path, body);
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
