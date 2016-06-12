package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Connection extends Http2EventAdapter implements HttpConnection {

  final Http2Client client;
  final ChannelHandlerContext context;
  private final io.netty.handler.codec.http2.Http2Connection connection;
  private final Http2ConnectionEncoder encoder;
  private final IntObjectMap<Http2Stream> streams = new IntObjectHashMap<>();
  private int numStreams;
  private long maxStreams;

  public Http2Connection(ChannelHandlerContext context,
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
      public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
        if (settings.maxConcurrentStreams() != null) {
          maxStreams = Math.min(client.maxConcurrentStream, settings.maxConcurrentStreams());
        }
      }

      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        Http2Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.headersHandler != null) {
            stream.headersHandler.accept(new HttpHeaders(headers));
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
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        Http2Stream stream = streams.get(streamId);
        if (stream != null) {
          if (stream.resetHandler != null) {
            stream.resetHandler.accept(new RstFrame());
          }
          endStream(streamId);
        }
      }

      private void endStream(int streamId) {
        numStreams--;
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
    return numStreams < maxStreams;
  }

  public void incrementConnectionWindowSize(int increment) {
    try {
      io.netty.handler.codec.http2.Http2Stream stream = connection.connectionStream();
      connection.local().flowController().incrementWindowSize(stream, increment);
    } catch (Http2Exception e) {
      e.printStackTrace();
    }
  }

  public void request(String method, String path, Consumer<HttpStream> handler) {
    numStreams++;
    int id = nextStreamId();
    Http2Stream stream = new Http2Stream(client, context, encoder, id, method, path);
    streams.put(id, stream);
    context.executor().execute(() -> {
      handler.accept(stream);
    });
  }

  private int nextStreamId() {
    return connection.local().incrementAndGetNextStreamId();
  }
}
