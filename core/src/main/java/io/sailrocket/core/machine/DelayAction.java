package io.sailrocket.core.machine;

import java.util.concurrent.TimeUnit;

public class DelayAction implements Action {
   private final long delay;
   private final TimeUnit timeUnit;
   private State nextState;

   public DelayAction(long delay, TimeUnit timeUnit) {
      this.delay = delay;
      this.timeUnit = timeUnit;
   }

   public void setNextState(State nextState) {
      this.nextState = nextState;
   }

   @Override
   public State invoke(Session session) {
      session.getScheduledExecutor().schedule(session.voidHandler(nextState, State.PROGRESS), delay, timeUnit);
      return nextState;
   }
}
