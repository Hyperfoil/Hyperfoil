package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BurstinessTest {

   @ParameterizedTest
   // expectedMaxEventsPerBucket is fixed because once we have the fix it will become 1
   @CsvSource({
         "500, 1", // Every 2ms, fires 1 event.
         "1000, 1", // Every 1ms, fires 1 event.
         "2000, 1", // Every 0.5ms, fires 1 event
         "10000, 1" // Every 0.1ms, fires 1 event
   })
   public void testMillisecondMicroBursting(double rate, int expectedMaxEventsPerBucket) {
      final RateGenerator generator = RateGenerator.constantRate(rate);
      final FireTimesCounter counter = new FireTimesCounter();
      int maxBurst = 0;
      long current = 0;
      int samples = 100;
      for (int i = 0; i < samples; i++) {
         counter.fireTimes = 0;
         long nextScheduled = generator.computeNextFireTime(current, counter);
         if (counter.fireTimes > maxBurst) {
            maxBurst = (int) counter.fireTimes;
         }
         current = nextScheduled;
      }
      assertEquals(expectedMaxEventsPerBucket, maxBurst,
            "At " + rate + " RPS, we expect a max burst of " + expectedMaxEventsPerBucket + " events per bucket.");
   }
}
