package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class StopwatchBeginStep implements Step, ResourceUtilizer {
   private final ObjectAccess key;

   public StopwatchBeginStep(ObjectAccess key) {
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
   public static class Builder extends BaseSequenceBuilder<Builder> implements StepBuilder<Builder> {
      // This constructor is going to be used only for service-loaded instantiation
      // to find the @Name annotation
      public Builder() {
         super(null);
         throw new UnsupportedOperationException();
      }

      public Builder(BaseSequenceBuilder<?> parent) {
         super(Objects.requireNonNull(parent));
      }

      @Override
      public List<Step> build() {
         // We're creating a new locator instead of using current since the new locator
         // should return this from .sequence(), too. On the other hand nothing will use
         // .step() as that is going to be shadowed for each step in buildSteps()
         List<Step> steps = new ArrayList<>();
         Object key = new Object();
         steps.add(new StopwatchBeginStep(SessionFactory.objectAccess(key)));
         steps.addAll(super.buildSteps());
         steps.add(new StopwatchEndStep(SessionFactory.readAccess(key), name()));
         return steps;
      }

      public BaseSequenceBuilder<?> endStep() {
         return parent;
      }
   }
}
