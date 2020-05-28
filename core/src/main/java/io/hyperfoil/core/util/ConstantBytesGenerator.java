package io.hyperfoil.core.util;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiFunction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ConstantBytesGenerator implements SerializableBiFunction<Session, Connection, ByteBuf> {
   private final byte[] bytes;

   public ConstantBytesGenerator(byte[] bytes) {
      this.bytes = bytes;
   }

   @Override
   public ByteBuf apply(Session session, Connection connection) {
      // TODO: implement pooling of wrapping buffers.
      // We cannot cache this buffer since despite the wrapped array is constant the code
      // writing the buffer to socket would mutate the ByteBuf (move readerIndex/writerIndex, decrement refCnt...)
      return Unpooled.wrappedBuffer(bytes);
   }
}
