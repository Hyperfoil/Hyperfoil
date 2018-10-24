package io.sailrocket.core.steps;

import io.sailrocket.api.session.Session;
import io.sailrocket.api.config.Step;

public class AwaitDelayStep implements Step {
   private final Object key;

   public AwaitDelayStep(Object key) {
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      ScheduleDelayStep.Timestamp blockedUntil = (ScheduleDelayStep.Timestamp) session.getObject(key);
      return System.currentTimeMillis() >= blockedUntil.timestamp;
   }
}
