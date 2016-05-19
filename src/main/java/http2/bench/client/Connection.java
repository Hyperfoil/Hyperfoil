package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Connection extends Http2EventAdapter {

  public static final AtomicInteger totalStreamCount = new AtomicInteger();

  private static final HashSet<Connection> all = new HashSet<>();

  public static int count() {
    synchronized (Connection.class) {
      return all.size();
    }
  }

  public static void shutdownAll() {
    HashSet<Connection> list;
    synchronized (Connection.class) {
      list = new HashSet<>(all);
    }
    for (Connection conn : list) {
      ChannelHandlerContext ctx = conn.context;
      ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
      ctx.close();
      ctx.flush();
    }
  }

  private final ChannelHandlerContext context;
  private final Http2Connection connection;
  private final Http2ConnectionEncoder encoder;
  private final IntObjectMap<Stream> streams = new IntObjectHashMap<>();
  private int numStreams;

  public Connection(ChannelHandlerContext context,
                    Http2Connection connection,
                    Http2ConnectionEncoder encoder,
                    Http2ConnectionDecoder decoder) {
    this.context = context;
    this.connection = connection;
    this.encoder = encoder;

    //
    synchronized (Connection.class) {
      all.add(this);
    }
    context.channel().closeFuture().addListener(v -> {
      synchronized (Connection.class) {
        all.remove(this);
      }
    });

    Http2EventAdapter listener = new Http2EventAdapter() {

      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.headersHandler != null) {
            stream.headersHandler.accept(new HeadersFrame(headers, endStream));
          }
          if (endStream) {
            endStream(streamId);
          }
        }
      }

      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
        int ack = super.onDataRead(ctx, streamId, data, padding, endOfStream);
        Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.dataHandler != null) {
            stream.dataHandler.accept(new DataFrame(endOfStream));
          }
          if (endOfStream) {
            endStream(streamId);
          }
        }
        return ack;
      }

      @Override
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.resetHandler != null) {
            stream.resetHandler.accept(new RstFrame());
          }
          endStream(streamId);
        }
      }

      private void endStream(int streamId) {
        numStreams--;
        Stream stream = streams.remove(streamId);
        if (stream != null && !stream.ended && stream.endHandler != null) {
          stream.ended = true;
          stream.endHandler.accept(null);
        }
      }
    };

    connection.addListener(listener);
    decoder.frameListener(listener);
  }

  public int numActiveStreams() {
    return numStreams;
  }

  public void request(String method, String path, Consumer<Stream> handler) {
    numStreams++;
    int id = nextStreamId();
    Stream stream = new Stream(context, encoder, id, method, path);
    streams.put(id, stream);
    handler.accept(stream);
  }

  private int nextStreamId() {
    return connection.local().incrementAndGetNextStreamId();
  }
}
