package io.hyperfoil.core.test;

import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.impl.LocalBenchmarkData;

public class TestUtil {
   private static final Locator TESTING_MOCK = new Locator() {
      @Override
      public StepBuilder<?> step() {
         throw new UnsupportedOperationException();
      }

      @Override
      public BaseSequenceBuilder sequence() {
         throw new UnsupportedOperationException();
      }

      @Override
      public ScenarioBuilder scenario() {
         throw new UnsupportedOperationException();
      }

      @Override
      public BenchmarkBuilder benchmark() {
         return new BenchmarkBuilder(null, new LocalBenchmarkData());
      }
   };

   public static String randomString(ThreadLocalRandom rand, int maxLength) {
      int length = rand.nextInt(maxLength);
      char[] chars = new char[length];
      for (int i = 0; i < length; ++i) {
         chars[i] = (char) rand.nextInt('a', 'z' + 1);
      }
      return String.valueOf(chars);
   }

   public static Locator locator() {
      return TESTING_MOCK;
   }
}
