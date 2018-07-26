package io.sailrocket.core.client.netty;

import io.sailrocket.core.client.HttpMethod;
import io.sailrocket.core.client.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Connection extends Http2EventAdapter implements HttpConnection {

  private final Http2Client client;
  private final ChannelHandlerContext context;
  private final io.netty.handler.codec.http2.Http2Connection connection;
  private final Http2ConnectionEncoder encoder;
  private final IntObjectMap<Http2Stream> streams = new IntObjectHashMap<>();
  private AtomicInteger numStreams = new AtomicInteger();
  private long maxStreams;

  Http2Connection(ChannelHandlerContext context,
                         io.netty.handler.codec.http2.Http2Connection connection,
                         Http2ConnectionEncoder encoder,
                         Http2ConnectionDecoder decoder,
                         Http2Client client) {
    this.client = client;
    this.context = context;
    this.connection = connection;
    this.encoder = encoder;
    this.maxStreams = client.maxConcurrentStream;

    //
    Http2EventAdapter listener = new Http2EventAdapter() {

      @Override
      public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        if (settings.maxConcurrentStreams() != null) {
          maxStreams = Math.min(client.maxConcurrentStream, settings.maxConcurrentStreams());
        }
      }

      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        Http2Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.headersHandler != null) {
            int code = -1;
            try {
              code = Integer.parseInt(headers.status().toString());
            } catch (NumberFormatException ignore) {
            }
            stream.headersHandler.accept(code);
          }
          if (endStream) {
            endStream(streamId);
          }
        }
      }

      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        int ack = super.onDataRead(ctx, streamId, data, padding, endOfStream);
        Http2Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.dataHandler != null) {
            stream.dataHandler.accept(data);
          }
          if (endOfStream) {
            endStream(streamId);
          }
        }
        return ack;
      }

      @Override
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        Http2Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.resetHandler != null) {
            stream.resetHandler.accept(0);
          }
          endStream(streamId);
        }
      }

      private void endStream(int streamId) {
        numStreams.decrementAndGet();
        Http2Stream stream = streams.remove(streamId);
        if (stream != null && !stream.ended && stream.endHandler != null) {
          stream.ended = true;
          stream.endHandler.accept(null);
        }
      }
    };

    connection.addListener(listener);
    decoder.frameListener(listener);
  }

  @Override
  public ChannelHandlerContext context() {
    return context;
  }

  @Override
  public boolean isAvailable() {
    return numStreams.get() < maxStreams;
  }

  @Override
  public int inflight() {
    return numStreams.get();
  }

  public void incrementConnectionWindowSize(int increment) {
    try {
      io.netty.handler.codec.http2.Http2Stream stream = connection.connectionStream();
      connection.local().flowController().incrementWindowSize(stream, increment);
    } catch (Http2Exception e) {
      e.printStackTrace();
    }
  }

  static class Http2Stream {

    final Http2Headers headers;
    final ByteBuf buff;
    final IntConsumer headersHandler;
    final Consumer<ByteBuf> dataHandler;
    final IntConsumer resetHandler;
    final Consumer<Void> endHandler;
    boolean ended;

    Http2Stream(Http2Headers headers, ByteBuf buff, IntConsumer headersHandler, Consumer<ByteBuf> dataHandler, IntConsumer resetHandler, Consumer<Void> endHandler) {
      this.headers = headers;
      this.buff = buff;
      this.headersHandler = headersHandler;
      this.dataHandler = dataHandler;
      this.resetHandler = resetHandler;
      this.endHandler = endHandler;
    }
  }

  void bilto(Http2Stream stream) {
    context.executor().execute(() -> {
      int id = nextStreamId();
      streams.put(id, stream);
      encoder.writeHeaders(context, id, stream.headers, 0, stream.buff == null, context.newPromise());
      if (stream.buff != null) {
        encoder.writeData(context, id, stream.buff, 0, true, context.newPromise());
      }
      context.flush();
    });
  }

  public HttpRequest request(HttpMethod method, String path) {
    numStreams.incrementAndGet();
    return new Http2Request(client, this, method, path);
  }

  private int nextStreamId() {
    return connection.local().incrementAndGetNextStreamId();
  }
}
