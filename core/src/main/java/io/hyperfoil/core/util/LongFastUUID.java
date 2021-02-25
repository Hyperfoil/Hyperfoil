package io.hyperfoil.core.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generate UUID based on the following match: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}
 */
public class LongFastUUID {
   private LongFastUUID() {

   }

   private static Method fastUUID;
   static {
      try {
         fastUUID = Long.class.getDeclaredMethod("fastUUID", long.class, long.class);
      } catch (NoSuchMethodException e) {
         throw new IllegalArgumentException(e);
      }
      fastUUID.setAccessible(true);
   }

   public static String randomUUID() {
      try {
         Random r = ThreadLocalRandom.current();
         return (String) fastUUID.invoke(null, r.nextLong(), r.nextLong());
      } catch (IllegalAccessException | InvocationTargetException e) {
         throw new IllegalStateException("Cannot generate UUID", e);
      }
   }
}
