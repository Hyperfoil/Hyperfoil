package io.sailrocket.core.client.netty;

import io.netty.handler.codec.http.HttpContent;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.sailrocket.api.HttpResponseHandlers;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {

  final Http1XClientPool client;
  // Todo not use concurrent
  private final Deque<HttpStream> inflights = new ConcurrentLinkedDeque<>();
  ChannelHandlerContext ctx;
  private AtomicInteger size = new AtomicInteger();

  HttpStream createStream(DefaultFullHttpRequest msg, HttpResponseHandlers handlers) {
      return new HttpStream(msg, handlers);
  }

  class HttpStream implements Runnable {

    private final DefaultFullHttpRequest msg;
    private final HttpResponseHandlers handlers;

    HttpStream(DefaultFullHttpRequest msg, HttpResponseHandlers handlers) {
      this.msg = msg;
      this.handlers = handlers;
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
      if (request.handlers.statusHandler() != null) {
        request.handlers.statusHandler().accept(response.status().code());
      }
      if (request.handlers.headerHandler() != null) {
        for (Map.Entry<String, String> header : response.headers()) {
          request.handlers.headerHandler().accept(header.getKey(), header.getValue());
        }
      }
    }
    if (msg instanceof HttpContent) {
      HttpStream request = inflights.peek();
      if (request.handlers.dataHandler() != null) {
        request.handlers.dataHandler().accept(((HttpContent) msg).content());
      }
    }
    if (msg instanceof LastHttpContent) {
      size.decrementAndGet();
      HttpStream request = inflights.poll();

      if (request.handlers.endHandler() != null) {
        request.handlers.endHandler().run();
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
