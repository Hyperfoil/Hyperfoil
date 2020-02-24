package io.hyperfoil.core.util;

import java.io.IOException;
import java.io.ObjectInputStream;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiFunction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ConstantBytesGenerator implements SerializableBiFunction<Session, Connection, ByteBuf> {
   private final byte[] bytes;
   // Byte buffers are in general non-serializable
   private transient ByteBuf buf;

   public ConstantBytesGenerator(byte[] bytes) {
      this.bytes = bytes;
      this.buf = Unpooled.wrappedBuffer(bytes);
   }

   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      this.buf = Unpooled.wrappedBuffer(bytes);
   }

   @Override
   public ByteBuf apply(Session session, Connection connection) {
      return buf;
   }
}
