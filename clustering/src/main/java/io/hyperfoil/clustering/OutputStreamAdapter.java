package io.hyperfoil.clustering;

import java.io.OutputStream;

import io.netty.buffer.Unpooled;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

public class OutputStreamAdapter extends OutputStream {
   private final WriteStream<Buffer> stream;

   public OutputStreamAdapter(WriteStream<Buffer> stream) {
      this.stream = stream;
   }

   @Override
   public void write(byte[] b) {
      stream.write(Buffer.buffer(Unpooled.wrappedBuffer(b)));
   }

   @Override
   public void write(byte[] b, int off, int len) {
      stream.write(Buffer.buffer(Unpooled.wrappedBuffer(b, off, len)));
   }

   @Override
   public void close() {
      stream.end();
   }

   @Override
   public void write(int b) {
      Buffer buffer = Buffer.buffer(1);
      buffer.appendByte((byte) b);
      stream.write(buffer);
   }
}
