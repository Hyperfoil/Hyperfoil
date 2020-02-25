package io.hyperfoil.core.test;

import java.util.concurrent.ThreadLocalRandom;

public class TestUtil {
   public static String randomString(ThreadLocalRandom rand, int maxLength) {
      int length = rand.nextInt(maxLength);
      char[] chars = new char[length];
      for (int i = 0; i < length; ++i) {
         chars[i] = (char) rand.nextInt('a', 'z' + 1);
      }
      return String.valueOf(chars);
   }
}
