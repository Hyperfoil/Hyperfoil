package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class StopwatchBeginStep implements Step, ResourceUtilizer {
   private final Access key;

   public StopwatchBeginStep(Object key) {
      this.key = SessionFactory.access(key);
   }

   @Override
   public boolean invoke(Session session) {
      // Setting timestamp only when it's set allows looping into stopwatch
      if (!key.isSet(session)) {
         StartTime startTime = (StartTime) key.activate(session);
         startTime.timestampMillis = System.currentTimeMillis();
         startTime.timestampNanos = System.nanoTime();
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      key.declareObject(session);
      key.setObject(session, new StartTime());
      key.unset(session);
   }

   static class StartTime {
      long timestampMillis;
      long timestampNanos;
   }

   public static class Builder extends BaseSequenceBuilder implements StepBuilder {
      public Builder(BaseSequenceBuilder parent) {
         super(parent);
         parent.stepBuilder(this);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         List<Step> steps = new ArrayList<>();
         Object key = new Object();
         steps.add(new StopwatchBeginStep(key));
         steps.addAll(super.buildSteps(sequence));
         steps.add(new StopwatchEndStep(sequence, key));
         return steps;
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
      }
   }
}
