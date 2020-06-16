package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class StopwatchBeginStep implements Step, ResourceUtilizer {
   private final Access key;

   public StopwatchBeginStep(Access key) {
      this.key = key;
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

   /**
    * Run nested sequence of steps, recording execution time.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("stopwatch")
   public static class Builder extends BaseSequenceBuilder implements StepBuilder<Builder> {
      public Builder() {
         super(null);
      }

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
         parent.stepBuilder(this);
      }

      @Override
      public List<Step> build() {
         // We're creating a new locator instead of using current since the new locator
         // should return this from .sequence(), too. On the other hand nothing will use
         // .step() as that is going to be shadowed for each step in buildSteps()
         List<Step> steps = new ArrayList<>();
         Object key = new Object();
         steps.add(new StopwatchBeginStep(SessionFactory.access(key)));
         steps.addAll(super.buildSteps());
         steps.add(new StopwatchEndStep(SessionFactory.access(key), name()));
         return steps;
      }

      public BaseSequenceBuilder endStep() {
         return parent;
      }
   }
}
