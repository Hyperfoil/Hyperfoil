package io.hyperfoil.core.test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class TestClock extends Clock {
   private Instant instant = Instant.now();

   @Override
   public ZoneId getZone() {
      return ZoneId.systemDefault();
   }

   @Override
   public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Instant instant() {
      return instant;
   }

   public void advance(long millis) {
      instant = instant.plusMillis(millis);
   }
}
