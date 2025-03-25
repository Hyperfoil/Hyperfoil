package io.hyperfoil.core.handlers.json;

import java.util.function.Consumer;
import java.util.function.Function;

public class ByteArrayByteStream implements ByteStream {
   private final Function<ByteStream, ByteStream> retain;
   private final Consumer<ByteStream> release;
   private byte[] array;
   private int readerIndex;

   public ByteArrayByteStream(Function<ByteStream, ByteStream> retain, Consumer<ByteStream> release) {
      this.retain = retain;
      this.release = release;
   }

   public byte[] array() {
      return array;
   }

   @Override
   public int getByte(int index) {
      return array[index];
   }

   @Override
   public int writerIndex() {
      return array.length;
   }

   @Override
   public int readerIndex() {
      return readerIndex;
   }

   @Override
   public void release() {
      release.accept(this);
   }

   @Override
   public ByteStream retain() {
      return retain.apply(this);
   }

   @Override
   public void moveTo(ByteStream other) {
      ByteArrayByteStream o = (ByteArrayByteStream) other;
      o.array = array;
      o.readerIndex = readerIndex;
      array = null;
   }

   public ByteArrayByteStream wrap(byte[] array) {
      this.array = array;
      this.readerIndex = 0;
      return this;
   }
}
