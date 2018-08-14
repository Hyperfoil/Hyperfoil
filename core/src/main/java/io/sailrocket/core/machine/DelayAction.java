package io.sailrocket.core.machine;

import java.util.concurrent.TimeUnit;

public class DelayAction implements Action {
   private final long delay;
   private final TimeUnit timeUnit;
   private final State handler;

   public DelayAction(long delay, TimeUnit timeUnit, State handler) {
      this.delay = delay;
      this.timeUnit = timeUnit;
      this.handler = handler;
   }

   @Override
   public void invoke(Session session) {
      session.getScheduledExecutor().schedule(session.voidHandler(handler, State.PROGRESS), delay, timeUnit);
   }
}
