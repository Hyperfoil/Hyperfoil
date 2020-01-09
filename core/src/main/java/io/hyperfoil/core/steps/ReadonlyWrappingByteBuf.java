package io.hyperfoil.core.steps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import io.netty.buffer.AbstractReferenceCountedByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;

// TODO: It would be nice to pool instances returned by slice()/retainedSlice
public class ReadonlyWrappingByteBuf extends AbstractReferenceCountedByteBuf {
   private byte[] array;

   protected ReadonlyWrappingByteBuf() {
      super(0);
   }

   public ReadonlyWrappingByteBuf wrap(byte[] array) {
      this.array = array;
      writerIndex(array.length);
      maxCapacity(array.length);
      return this;
   }

   @Override
   public boolean isReadOnly() {
      return true;
   }

   @Override
   protected byte _getByte(int index) {
      return array[index];
   }

   @Override
   protected short _getShort(int index) {
      return (short) (array[index] << 8 | array[index + 1] & 0xFF);
   }

   @Override
   protected short _getShortLE(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected int _getUnsignedMedium(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected int _getUnsignedMediumLE(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected int _getInt(int index) {
      return (array[index] & 0xff) << 24 |
            (array[index + 1] & 0xff) << 16 |
            (array[index + 2] & 0xff) << 8 |
            array[index + 3] & 0xff;
   }

   @Override
   protected int _getIntLE(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected long _getLong(int index) {
      return ((long) array[index] & 0xff) << 56 |
            ((long) array[index + 1] & 0xff) << 48 |
            ((long) array[index + 2] & 0xff) << 40 |
            ((long) array[index + 3] & 0xff) << 32 |
            ((long) array[index + 4] & 0xff) << 24 |
            ((long) array[index + 5] & 0xff) << 16 |
            ((long) array[index + 6] & 0xff) << 8 |
            (long) array[index + 7] & 0xff;
   }

   @Override
   protected long _getLongLE(int index) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setByte(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setShort(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setShortLE(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setMedium(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setMediumLE(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setInt(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setIntLE(int index, int value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setLong(int index, long value) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected void _setLongLE(int index, long value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int capacity() {
      return array.length;
   }

   @Override
   public ByteBuf capacity(int newCapacity) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ByteBufAllocator alloc() {
      return PooledByteBufAllocator.DEFAULT;
   }

   @SuppressWarnings("deprecation")
   @Override
   public ByteOrder order() {
      return ByteOrder.BIG_ENDIAN;
   }

   @Override
   public ByteBuf unwrap() {
      return null;
   }

   @Override
   public boolean isDirect() {
      return false;
   }

   @Override
   public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
      checkDstIndex(index, length, dstIndex, dst.capacity());
      if (dst.hasMemoryAddress()) {
         PlatformDependent.copyMemory(array, index, dst.memoryAddress() + dstIndex, length);
      } else if (dst.hasArray()) {
         getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
      } else {
         dst.setBytes(dstIndex, array, index, length);
      }
      return this;
   }

   @Override
   public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
      System.arraycopy(array, index, dst, dstIndex, length);
      return this;
   }

   @Override
   public ByteBuf getBytes(int index, ByteBuffer dst) {
      dst.put(array, index, dst.remaining());
      return this;
   }

   @Override
   public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
      out.write(array, index, length);
      return this;
   }

   @Override
   public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
      ByteBuffer tmpBuf = ByteBuffer.wrap(array);
      return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length));
   }

   @Override
   public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
      ByteBuffer tmpBuf = ByteBuffer.wrap(array);
      return out.write((ByteBuffer) tmpBuf.clear().position(index).limit(index + length), position);
   }

   @Override
   public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ByteBuf setBytes(int index, ByteBuffer src) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int setBytes(int index, InputStream in, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int setBytes(int index, ScatteringByteChannel in, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int setBytes(int index, FileChannel in, long position, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ByteBuf copy(int index, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public int nioBufferCount() {
      return 0;
   }

   @Override
   public ByteBuffer nioBuffer(int index, int length) {
      if (index < 0 || length < 0 || index + length > array.length) {
         throw new IllegalArgumentException();
      }
      return ByteBuffer.wrap(array, index, length);
   }

   @Override
   public ByteBuffer internalNioBuffer(int index, int length) {
      return null;
   }

   @Override
   public ByteBuffer[] nioBuffers(int index, int length) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean hasArray() {
      return true;
   }

   @Override
   public byte[] array() {
      return array;
   }

   @Override
   public int arrayOffset() {
      return 0;
   }

   @Override
   public boolean hasMemoryAddress() {
      return false;
   }

   @Override
   public long memoryAddress() {
      return 0;
   }

   @Override
   protected void deallocate() {
      array = EmptyArrays.EMPTY_BYTES;
   }
}
