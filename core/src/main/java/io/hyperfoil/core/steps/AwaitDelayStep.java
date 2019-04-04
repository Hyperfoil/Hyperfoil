package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class AwaitDelayStep implements Step {
   private final Access key;

   public AwaitDelayStep(Object key) {
      this.key = SessionFactory.access(key);
   }

   @Override
   public boolean invoke(Session session) {
      ScheduleDelayStep.Timestamp blockedUntil = (ScheduleDelayStep.Timestamp) key.getObject(session);
      return System.currentTimeMillis() >= blockedUntil.timestamp;
   }

   public static class Builder extends BaseStepBuilder {
      private Object key;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder key(String key) {
         this.key = key;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new AwaitDelayStep(key));
      }
   }
}
