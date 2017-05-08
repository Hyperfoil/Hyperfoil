package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {

  private final Http1xClient client;
  private final Deque<HttpStreamImpl> requests = new ConcurrentLinkedDeque<>();
  private ChannelHandlerContext ctx;
  private volatile int size;

  public Http1xConnection(Http1xClient client) {
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
      HttpStreamImpl request = requests.peek();
      if (request.headersHandler != null) {
        request.headersHandler.accept(new HttpHeaders(response));
      }
    }
    if (msg instanceof LastHttpContent) {
      size--;
      HttpStreamImpl request = requests.poll();
      if (request.endHandler != null) {
        request.endHandler.accept(null);
      }
    }
    super.channelRead(ctx, msg);
  }

  private class HttpStreamImpl implements HttpStream {
    private final String method;
    private final String path;
    private Map<String, String> headers;
    private Consumer<HttpHeaders> headersHandler;
    private Consumer<RstFrame> resetHandler;
    private Consumer<Void> endHandler;

    public HttpStreamImpl(String method, String path) {
      this.method = method;
      this.path = path;
      headers = new HashMap<>();
    }

    @Override
    public HttpStream putHeader(String name, String value) {
      headers.put(name, value);
      return this;
    }

    @Override
    public HttpStream headersHandler(Consumer<HttpHeaders> handler) {
      headersHandler = handler;
      return this;
    }

    @Override
    public HttpStream resetHandler(Consumer<RstFrame> handler) {
      resetHandler = handler;
      return this;
    }

    @Override
    public HttpStream endHandler(Consumer<Void> handler) {
      endHandler = handler;
      return this;
    }

    @Override
    public void end(ByteBuf buff) {
      if (buff == null) {
        buff = Unpooled.EMPTY_BUFFER;
      }
      DefaultFullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), path, buff, false);
      headers.forEach(msg.headers()::add);
      ctx.writeAndFlush(msg);
    }
  }

  @Override
  public void request(String method, String path, Consumer<HttpStream> handler) {
    HttpStreamImpl request = new HttpStreamImpl(method, path);
    requests.add(request);
    size++;
    ctx.executor().execute(() -> {
      handler.accept(request);
    });
  }

  @Override
  public ChannelHandlerContext context() {
    return ctx;
  }

  @Override
  public boolean isAvailable() {
    return size < client.maxConcurrentStream;
  }
}
