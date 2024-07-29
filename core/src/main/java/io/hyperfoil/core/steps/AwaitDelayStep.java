package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class AwaitDelayStep implements Step {
   private final ReadAccess key;

   public AwaitDelayStep(ReadAccess key) {
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      ScheduleDelayStep.Timestamp blockedUntil = (ScheduleDelayStep.Timestamp) key.getObject(session);
      return System.currentTimeMillis() >= blockedUntil.timestamp;
   }

   /**
    * Block this sequence until referenced delay point.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("awaitDelay")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private Object key;

      /**
       * @param param Delay point created in <code>scheduleDelay.key</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return key(param);
      }

      /**
       * Delay point created in <code>scheduleDelay.key</code>.
       *
       * @param key Key.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new AwaitDelayStep(SessionFactory.readAccess(key)));
      }
   }
}
