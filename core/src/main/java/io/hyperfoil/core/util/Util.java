package io.hyperfoil.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.netty.buffer.ByteBuf;

public class Util {
   public static final CompletableFuture<Void> COMPLETED_VOID_FUTURE = CompletableFuture.completedFuture(null);
   private static final NumberFormatException NUMBER_FORMAT_EXCEPTION = new NumberFormatException();
   private static final int[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

   private Util() {}

   public static boolean compareIgnoreCase(byte b1, byte b2) {
      return b1 == b2 || toUpperCase(b1) == toUpperCase(b2) || toLowerCase(b1) == toLowerCase(b2);
   }

   public static byte toLowerCase(byte b) {
      return b >= 'A' && b <= 'Z' ? (byte) (b + 32) : b;
   }

   public static byte toUpperCase(byte b) {
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

   public static ByteBuf string2byteBuf(CharSequence str, ByteBuf buffer) {
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

   public static boolean startsWith(CharSequence sequence, int offset, CharSequence prefix) {
      return regionMatches(sequence, offset, prefix, 0, prefix.length());
   }

   public static int pow(int base, int exp) {
      int res = 1;
      while (exp-- > 0) res *= base;
      return res;
   }

   public static long parseLong(ByteBuf data, int offset, int length) {
      long value = 0;
      int i = offset;
      while (Character.isWhitespace(data.getByte(i))) ++i;
      byte sign = data.getByte(i);
      if (sign == '-' || sign == '+') ++i;
      while (Character.isWhitespace(data.getByte(i))) ++i;
      while (length > 0 && Character.isWhitespace(data.getByte(offset + length - 1))) --length;
      for (; i < offset + length; ++i) {
         byte digit = data.getByte(i);
         if (digit < '0' || digit > '9') {
            throw NUMBER_FORMAT_EXCEPTION;
         }
         value *= 10;
         value += digit - '0';
      }
      return sign == '-' ? -value : value;
   }

   public static boolean isParamConvertible(Class<?> type) {
      return type == String.class || type == CharSequence.class || type == Object.class || type.isPrimitive() || type.isEnum();
   }

   public static String prettyPrintObject(Object value) {
      if (value instanceof byte[]) {
         byte[] bytes = (byte[]) value;
         if (bytes.length == 0) {
            return "";
         }
         StringBuilder sb = new StringBuilder("[");
         sb.append((char) HEX[(bytes[0] >> 4)]);
         sb.append((char) HEX[(bytes[0] & 0xF)]);
         int length = Math.min(32, bytes.length);
         for (int i = 1; i < length; ++i) {
            sb.append(", ");
            sb.append((char) HEX[(bytes[i] >> 4)]);
            sb.append((char) HEX[(bytes[i] & 0xF)]);
         }
         if (bytes.length > 32) {
            sb.append(", ... (total length: ").append(bytes.length).append(")");
         }
         sb.append("]=");
         sb.append(new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.UTF_8));
         if (bytes.length > 32) {
            sb.append("...");
         }
         return sb.toString();
      } else {
         return String.valueOf(value);
      }
   }

   public static boolean hasPrefix(ByteBuf data, int offset, int length, byte[] prefix) {
      int i = 0;
      if (length < prefix.length) {
         return false;
      }
      for (; i < prefix.length; i++) {
         if (data.getByte(offset + i) != prefix[i]) {
            return false;
         }
      }
      return true;
   }

   public static byte[] serialize(Benchmark benchmark) throws IOException {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
         outputStream.writeObject(benchmark);
      }
      return byteArrayOutputStream.toByteArray();
   }

   public static Benchmark deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return (Benchmark) input.readObject();
      }
   }

   private static ByteArrayOutputStream toByteArrayOutputStream(InputStream stream) throws IOException {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = stream.read(buffer)) != -1) {
         result.write(buffer, 0, length);
      }
      stream.close();
      return result;
   }

   public static byte[] toByteArray(InputStream stream) throws IOException {
      return toByteArrayOutputStream(stream).toByteArray();
   }

   public static String toString(InputStream stream) throws IOException {
      return toByteArrayOutputStream(stream).toString(StandardCharsets.UTF_8.name());
   }

   public static long parseLong(CharSequence string) {
      return parseLong(string, 0, string.length(), 0);
   }

   public static long parseLong(CharSequence string, int begin, int end) {
      return parseLong(string, begin, end, 0);
   }

   public static long parseLong(CharSequence string, int begin, int end, long defaultValue) {
      long value = 0;
      int i = begin;
      char sign = string.charAt(begin);
      if (sign == '-' || sign == '+') ++i;
      for (; i < end; ++i) {
         int digit = string.charAt(i);
         if (digit < '0' || digit > '9') return defaultValue;
         value *= 10;
         value += digit - '0';
      }
      return sign == '-' ? -value : value;
   }

   public static long parseToNanos(String time) {
      TimeUnit unit;
      String prefix;
      if (time.endsWith("ms")) {
         unit = TimeUnit.MILLISECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("us")) {
         unit = TimeUnit.MICROSECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("ns")) {
         unit = TimeUnit.NANOSECONDS;
         prefix = time.substring(0, time.length() - 2);
      } else if (time.endsWith("s")) {
         unit = TimeUnit.SECONDS;
         prefix = time.substring(0, time.length() - 1);
      } else if (time.endsWith("m")) {
         unit = TimeUnit.MINUTES;
         prefix = time.substring(0, time.length() - 1);
      } else if (time.endsWith("h")) {
         unit = TimeUnit.HOURS;
         prefix = time.substring(0, time.length() - 1);
      } else {
         throw new BenchmarkDefinitionException("Unknown time unit: " + time);
      }
      return unit.toNanos(Long.parseLong(prefix.trim()));
   }

   public static long parseToMillis(String time) {
      time = time.trim();
      TimeUnit unit;
      String prefix;
      switch (time.charAt(time.length() - 1)) {
         case 's':
            if (time.endsWith("ms")) {
               unit = TimeUnit.MILLISECONDS;
               prefix = time.substring(0, time.length() - 2).trim();
            } else {
               unit = TimeUnit.SECONDS;
               prefix = time.substring(0, time.length() - 1).trim();
            }
            break;
         case 'm':
            unit = TimeUnit.MINUTES;
            prefix = time.substring(0, time.length() - 1).trim();
            break;
         case 'h':
            unit = TimeUnit.HOURS;
            prefix = time.substring(0, time.length() - 1).trim();
            break;
         default:
            unit = TimeUnit.SECONDS;
            prefix = time;
            break;
      }
      return unit.toMillis(Long.parseLong(prefix));
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
            buf.writeByte(HEX[(b >> 4) & 0xF]);
            buf.writeByte(HEX[b & 0xF]);
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
