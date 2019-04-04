package io.hyperfoil.core.handlers;

/**
 * Abstract wrapper over {@link io.netty.buffer.ByteBuf}, <code>byte[]</code> or {@link String}.
 */
public interface ByteStream {
   boolean isReadable();
   byte readByte();

   int getByte(int index);

   int writerIndex();

   int readerIndex();

   void release();

   ByteStream retain();

   void moveTo(ByteStream other);
}
