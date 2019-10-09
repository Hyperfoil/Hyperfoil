package io.hyperfoil.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;

public final class Util {
   private Util() {}

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
      TimeUnit unit;
      String prefix;
      switch (time.charAt(time.length() - 1)) {
         case 's':
            unit = TimeUnit.SECONDS;
            prefix = time.substring(0, time.length() - 1);
            break;
         case 'm':
            unit = TimeUnit.MINUTES;
            prefix = time.substring(0, time.length() - 1);
            break;
         case 'h':
            unit = TimeUnit.HOURS;
            prefix = time.substring(0, time.length() - 1);
            break;
         default:
            unit = TimeUnit.SECONDS;
            prefix = time;
            break;
      }
      return unit.toMillis(Long.parseLong(prefix));
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

   public static byte[] toByteArray(InputStream stream) throws IOException {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = stream.read(buffer)) != -1) {
         result.write(buffer, 0, length);
      }
      byte[] bytes = result.toByteArray();
      stream.close();
      return bytes;
   }

   public static UUID randomUUID() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      return new UUID(random.nextLong(), random.nextLong());
   }
}
