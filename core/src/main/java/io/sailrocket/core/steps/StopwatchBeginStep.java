package io.sailrocket.core.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.sailrocket.api.session.Session;
import io.sailrocket.api.config.Step;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.StepBuilder;

public class StopwatchBeginStep implements Step, ResourceUtilizer {
   private final Object key;

   public StopwatchBeginStep(Object key) {
      this.key = key;
   }

   @Override
   public void invoke(Session session) {
      // Setting timestamp only when it's set allows looping into stopwatch
      if (!session.isSet(key)) {
         StartTime startTime = (StartTime) session.activate(key);
         startTime.timestamp = System.nanoTime();
      }
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
         parent.step(this);
      }

      @Override
      public List<Step> build() {
         List<Step> steps = new ArrayList<>();
         Object key = new Object();
         steps.add(new StopwatchBeginStep(key));
         steps.addAll(this.steps.stream().flatMap(stepBuilder -> stepBuilder.build().stream()).collect(Collectors.toList()));
         steps.add(new StopwatchEndStep(key));
         return steps;
      }
   }
}
