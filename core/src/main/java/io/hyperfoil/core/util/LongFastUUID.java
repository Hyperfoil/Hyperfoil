package io.hyperfoil.core.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generate UUID based on the following match: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}
 */
public class LongFastUUID {
   private static char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

   private LongFastUUID() {
   }

   // Note: ideally we would use Long.fastUUID but that's not accessible in newer JDKs
   public static String randomUUID() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      long lsb = random.nextLong(), msb = random.nextLong();

      char[] buf = new char[36];
      set(msb >>> 32, buf, 0, 8);
      buf[8] = '-';
      set(msb >>> 16, buf, 9, 4);
      buf[13] = '-';
      set(msb, buf, 14, 4);
      buf[18] = '-';
      set(lsb >>> 48, buf, 19, 4);
      buf[23] = '-';
      set(lsb, buf, 24, 12);

      return new String(buf);
   }

   private static void set(long value, char[] buf, int offset, int length) {
      for (int index = offset + length - 1; index >= offset; --index) {
         buf[index] = DIGITS[(int) (value & 0xF)];
         value >>>= 4;
      }
   }
}
