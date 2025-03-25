package io.hyperfoil.core.handlers.json;

import java.util.function.Consumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;

public class ByteBufByteStream implements ByteStream {
   private final Function<ByteStream, ByteStream> retain;
   private final Consumer<ByteStream> release;
   private ByteBuf buffer;
   private int readerIndex, writerIndex;

   public ByteBufByteStream(Function<ByteStream, ByteStream> retain, Consumer<ByteStream> release) {
      this.retain = retain;
      this.release = release;
   }

   @Override
   public int getByte(int index) {
      return buffer.getByte(index);
   }

   @Override
   public int writerIndex() {
      return writerIndex;
   }

   @Override
   public int readerIndex() {
      return readerIndex;
   }

   @Override
   public void release() {
      buffer.release();
      buffer = null;
      readerIndex = -1;
      release.accept(this);
   }

   @Override
   public ByteStream retain() {
      buffer.retain();
      return retain.apply(this);
   }

   @Override
   public void moveTo(ByteStream other) {
      ByteBufByteStream o = (ByteBufByteStream) other;
      assert o.buffer == null;
      o.buffer = buffer;
      o.readerIndex = readerIndex;
      o.writerIndex = writerIndex;
      buffer = null;
      readerIndex = -1;
   }

   public ByteBuf buffer() {
      return buffer;
   }

   public ByteBufByteStream wrap(ByteBuf data, int readerIndex, int writerIndex) {
      this.buffer = data;
      this.readerIndex = readerIndex;
      this.writerIndex = writerIndex;
      return this;
   }
}
