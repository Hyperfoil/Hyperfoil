package io.hyperfoil.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

public class Util {
   private Util() {}

   public static boolean compareIgnoreCase(byte b1, byte b2) {
      return b1 == b2 || toUpperCase(b1) == toUpperCase(b2) || toLowerCase(b1) == toLowerCase(b2);
   }
   private static byte toLowerCase(byte b) {
      return b >= 'A' && b <= 'Z' ? (byte) (b + 32) : b;
   }

   private static byte toUpperCase(byte b) {
      return b >= 'a' && b <= 'z' ? (byte) (b - 32) : b;
   }

   /**
    * Pretty prints time in 9 spaces
    */
   public static String prettyPrintNanos(long meanResponseTime) {
      if (meanResponseTime < 1000) {
         return String.format("%6d ns", meanResponseTime);
      } else if (meanResponseTime < 1000_000) {
         return String.format("%6.2f μs", meanResponseTime / 1000d);
      } else if (meanResponseTime < 1000_000_000) {
         return String.format("%6.2f ms", meanResponseTime / 1000_000d);
      } else {
         return String.format("%6.2f s ", meanResponseTime / 1000_000_000d);
      }
   }

   public static String toString(ByteBuf buf, int offset, int length) {
      if (buf.hasArray()) {
         return new String(buf.array(), buf.arrayOffset() + offset, length, StandardCharsets.UTF_8);
      } else {
         byte[] strBytes = new byte[length];
         buf.getBytes(offset, strBytes, 0, length);
         return new String(strBytes, StandardCharsets.UTF_8);
      }
   }

   public static String toString(InputStream inputStream) throws IOException {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = inputStream.read(buffer)) != -1) {
         result.write(buffer, 0, length);
      }
      return result.toString(StandardCharsets.UTF_8.name());
   }

   public static ByteBuf string2byteBuf(String str, ByteBuf buffer) {
      // TODO: allocations everywhere but at least not the bytes themselves...
      CharBuffer input = CharBuffer.wrap(str);
      ByteBuffer output = buffer.nioBuffer(buffer.writerIndex(), buffer.capacity() - buffer.writerIndex());
      CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
      int accumulatedBytes = 0;
      for (;;) {
         CoderResult result = encoder.encode(input, output, true);
         if (result.isError()) {
            throw new RuntimeException("Cannot encode: " + result + ", string is " + str);
         } else if (result.isUnderflow()) {
            buffer.writerIndex(accumulatedBytes + output.position());
            return buffer;
         } else if (result.isOverflow()) {
            buffer.capacity(buffer.capacity() * 2);
            int writtenBytes = output.position();
            accumulatedBytes += writtenBytes;
            output = buffer.nioBuffer(accumulatedBytes, buffer.capacity() - accumulatedBytes);
         } else {
            throw new IllegalStateException();
         }
      }
   }

   public static String prettyPrintData(double value) {
      double scaled;
      String suffix;
      if (value >= 1024 * 1024 * 1024) {
         scaled = (double) value / (1024 * 1024 * 1024);
         suffix = "GB";
      } else if (value >= 1024 * 1024) {
         scaled = (double) value / (1024 * 1024);
         suffix = "MB";
      }  else if (value >= 1024) {
         scaled = (double) value / 1024;
         suffix = "kB";
      } else {
         scaled = value;
         suffix = "B ";
      }
      return String.format("%6.2f%s", scaled, suffix);
   }
}
