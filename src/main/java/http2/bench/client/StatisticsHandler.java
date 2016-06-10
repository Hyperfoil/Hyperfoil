package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@ChannelHandler.Sharable
class StatisticsHandler extends ChannelDuplexHandler {

  private final LongAdder bytesRead = new LongAdder();
  private final LongAdder bytesWritten = new LongAdder();

  long bytesRead() {
    return bytesRead.longValue();
  }

  long bytesWritten() {
    return bytesWritten.longValue();
  }

  void reset() {
    bytesRead.reset();
    bytesWritten.reset();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    int size = sizeOf(msg);
    if (size > 0) {
      bytesRead.add(size);
    }
    super.channelRead(ctx, msg);
  }
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    int size = sizeOf(msg);
    if (size > 0) {
      bytesWritten.add(size);
    }
    super.write(ctx, msg, promise);
  }

  private int sizeOf(Object msg) {
    if (msg instanceof ByteBuf) {
      return ((ByteBuf) msg).readableBytes();
    } else if (msg instanceof ByteBufHolder) {
      return ((ByteBufHolder) msg).content().readableBytes();
    } else {
      return 0;
    }
  }
}
