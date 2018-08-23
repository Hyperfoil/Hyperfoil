package io.sailrocket.core.machine;

import java.util.concurrent.TimeUnit;

public class DelayStep implements Step {
   private final long delay;
   private final TimeUnit timeUnit;

   public DelayStep(long delay, TimeUnit timeUnit) {
      this.delay = delay;
      this.timeUnit = timeUnit;
   }

   @Override
   public void invoke(Session session) {
      session.getScheduledExecutor().schedule(session.progress(), delay, timeUnit);
   }
}
