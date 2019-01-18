package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.StepBuilder;
import io.hyperfoil.function.SerializableSupplier;

public class StopwatchBeginStep implements Step, ResourceUtilizer {
   private final Object key;

   public StopwatchBeginStep(Object key) {
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      // Setting timestamp only when it's set allows looping into stopwatch
      if (!session.isSet(key)) {
         StartTime startTime = (StartTime) session.activate(key);
         startTime.timestamp = System.nanoTime();
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declare(key);
      session.setObject(key, new StartTime());
      session.unset(key);
   }

   static class StartTime {
      long timestamp;
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
         steps.addAll(this.steps.stream().flatMap(stepBuilder -> stepBuilder.build(sequence).stream()).collect(Collectors.toList()));
         steps.add(new StopwatchEndStep(key));
         return steps;
      }

      @Override
      public <T extends StepBuilder> void forEach(Class<T> type, Consumer<T> consumer) {
         for (StepBuilder step : steps) {
            step.forEach(type, consumer);
         }
         StepBuilder.super.forEach(type, consumer);
      }
   }
}
