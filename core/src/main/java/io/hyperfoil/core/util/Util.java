package io.hyperfoil.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;

public class Util {
   public static final CompletableFuture<Void> COMPLETED_VOID_FUTURE = CompletableFuture.completedFuture(null);

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
    *
    * @param meanResponseTime Time in nanoseconds.
    * @return Formatted string.
    */
   public static String prettyPrintNanosFixed(long meanResponseTime) {
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

   public static String prettyPrintNanos(long meanResponseTime) {
      if (meanResponseTime < 1000) {
         return String.format("%d ns", meanResponseTime);
      } else if (meanResponseTime < 1000_000) {
         return String.format("%.2f μs", meanResponseTime / 1000d);
      } else if (meanResponseTime < 1000_000_000) {
         return String.format("%.2f ms", meanResponseTime / 1000_000d);
      } else {
         return String.format("%.2f s ", meanResponseTime / 1000_000_000d);
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
      int accumulatedBytes = buffer.writerIndex();
      for (; ; ) {
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

   public static String explainCauses(Throwable e) {
      StringBuilder causes = new StringBuilder();
      Set<Throwable> reported = new HashSet<>();
      while (e != null && !reported.contains(e)) {
         if (causes.length() != 0) {
            causes.append(": ");
         }
         causes.append(e.getMessage());
         reported.add(e);
         e = e.getCause();
      }
      return causes.toString();
   }

   public static boolean regionMatchesIgnoreCase(CharSequence a, int aoffset, CharSequence b, int boffset, int length) {
      if (a instanceof String && b instanceof String) {
         return ((String) a).regionMatches(true, aoffset, (String) b, boffset, length);
      }
      if (aoffset < 0 || boffset < 0) {
         return false;
      } else if (aoffset + length > a.length() || boffset + length > b.length()) {
         return false;
      }
      while (length-- > 0) {
         char c1 = a.charAt(aoffset++);
         char c2 = b.charAt(boffset++);
         if (c1 == c2) {
            continue;
         }
         char u1 = Character.toUpperCase(c1);
         char u2 = Character.toUpperCase(c2);
         if (u1 == u2) {
            continue;
         }
         if (Character.toLowerCase(u1) != Character.toLowerCase(u2)) {
            return false;
         }
      }
      return true;
   }

   public static boolean regionMatches(CharSequence a, int aoffset, CharSequence b, int boffset, int length) {
      if (a instanceof String && b instanceof String) {
         return ((String) a).regionMatches(aoffset, (String) b, boffset, length);
      }
      if (aoffset < 0 || boffset < 0) {
         return false;
      } else if (aoffset + length > a.length() || boffset + length > b.length()) {
         return false;
      }
      while (length-- > 0) {
         char c1 = a.charAt(aoffset++);
         char c2 = b.charAt(boffset++);
         if (c1 != c2) {
            return false;
         }
      }
      return true;
   }

   private static class URLEncoding {
      private static final BitSet DONT_NEED_ENCODING = new BitSet();

      static {
         for (int i = 'a'; i <= 'z'; i++) {
            DONT_NEED_ENCODING.set(i);
         }
         for (int i = 'A'; i <= 'Z'; i++) {
            DONT_NEED_ENCODING.set(i);
         }
         for (int i = '0'; i <= '9'; i++) {
            DONT_NEED_ENCODING.set(i);
         }
         DONT_NEED_ENCODING.set('-');
         DONT_NEED_ENCODING.set('_');
         DONT_NEED_ENCODING.set('.');
         DONT_NEED_ENCODING.set('*');
      }

      private static final int[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
   }

   public static void urlEncode(String string, ByteBuf buf) {
      // TODO: more efficient implementation without allocation
      byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
      for (byte b : bytes) {
         if (b >= 0 && URLEncoding.DONT_NEED_ENCODING.get(b)) {
            buf.ensureWritable(1);
            buf.writeByte(b);
         } else if (b == ' ') {
            buf.ensureWritable(1);
            buf.writeByte('+');
         } else {
            buf.ensureWritable(3);
            buf.writeByte('%');
            buf.writeByte(URLEncoding.HEX[(b >> 4) & 0xF]);
            buf.writeByte(URLEncoding.HEX[b & 0xF]);
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
      } else if (value >= 1024) {
         scaled = (double) value / 1024;
         suffix = "kB";
      } else {
         scaled = value;
         suffix = "B ";
      }
      return String.format("%6.2f%s", scaled, suffix);
   }

   private static final int[] SIZE_TABLE = new int[]{
         1_000_000_000, 100_000_000, 10_000_000, 1_000_000, 100_000, 10_000, 1000, 100, 10
   };

   public static void intAsText2byteBuf(int value, ByteBuf buf) {
      if (value < 0) {
         buf.writeByte('-');
         value = -value;
      }
      int i = 0;
      for (; i < SIZE_TABLE.length; ++i) {
         if (value >= SIZE_TABLE[i]) break;
      }
      for (; i < SIZE_TABLE.length; ++i) {
         int q = value / SIZE_TABLE[i];
         assert q >= 0 && q <= 9;
         buf.writeByte('0' + q);
         value -= q * SIZE_TABLE[i];
      }
      assert value >= 0 && value <= 9;
      buf.writeByte('0' + value);
   }
}
