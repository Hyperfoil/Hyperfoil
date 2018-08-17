package io.sailrocket.core.machine;

import java.util.concurrent.TimeUnit;

public class DelayAction implements Action {
   private final long delay;
   private final TimeUnit timeUnit;

   public DelayAction(long delay, TimeUnit timeUnit) {
      this.delay = delay;
      this.timeUnit = timeUnit;
   }

   @Override
   public void invoke(Session session) {
      session.getScheduledExecutor().schedule(session.progress(), delay, timeUnit);
   }
}
