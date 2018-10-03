package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.sailrocket.api.config.Step;

public abstract class BaseSequenceBuilder implements Rewritable<BaseSequenceBuilder> {
   protected final BaseSequenceBuilder parent;
   protected final List<StepBuilder> steps = new ArrayList<>();

   public BaseSequenceBuilder(BaseSequenceBuilder parent) {
      this.parent = parent;
   }

   public StepDiscriminator step() {
       return new StepDiscriminator(this);
   }

   public BaseSequenceBuilder step(Step step) {
      steps.add(() -> Collections.singletonList(step));
      return this;
   }

   public BaseSequenceBuilder step(StepBuilder stepBuilder) {
      steps.add(stepBuilder);
      return this;
   }

   public SequenceBuilder end() {
      return parent.end();
   }

   public ScenarioBuilder endSequence() {
      return end().endSequence();
   }

   @Override
   public void readFrom(BaseSequenceBuilder other) {
      assert steps.isEmpty();
      steps.addAll(other.steps);
   }
}
