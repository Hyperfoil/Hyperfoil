package io.sailrocket.core.steps;

import java.util.concurrent.TimeUnit;

import io.sailrocket.api.Step;
import io.sailrocket.api.Session;

public class DelayStep implements Step {
   private final long delay;
   private final TimeUnit timeUnit;

   public DelayStep(long delay, TimeUnit timeUnit) {
      this.delay = delay;
      this.timeUnit = timeUnit;
   }

   @Override
   public void invoke(Session session) {
      session.httpClientPool().schedule((Runnable) session, delay, timeUnit);
   }
}
