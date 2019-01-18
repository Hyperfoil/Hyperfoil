package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.function.SerializableSupplier;

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
      steps.add((SerializableSupplier<Sequence> sequence) -> Collections.singletonList(step));
      return this;
   }

   // Calling this method step() would cause ambiguity with step(Step) defined through lambda
   public BaseSequenceBuilder stepBuilder(StepBuilder stepBuilder) {
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
