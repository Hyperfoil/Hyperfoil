package io.hyperfoil.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.hyperfoil.function.SerializableSupplier;
import io.hyperfoil.impl.StepCatalogFactory;

public abstract class BaseSequenceBuilder implements Rewritable<BaseSequenceBuilder> {
   private static final StepCatalogFactory sdf;

   protected final BaseSequenceBuilder parent;
   protected final List<StepBuilder> steps = new ArrayList<>();

   static {
      StepCatalogFactory singleSdf = null;
      for (StepCatalogFactory sdf : ServiceLoader.load(StepCatalogFactory.class)) {
         singleSdf = sdf;
      }
      sdf = singleSdf;
   }

   public BaseSequenceBuilder(BaseSequenceBuilder parent) {
      this.parent = parent;
   }

   public <D extends Step.Catalog> D step(Class<D> catalogClass) {
      if (sdf == null) {
         throw new IllegalStateException("Cannot load step catalog");
      }
      Step.Catalog catalog = sdf.create(this);
      if (catalogClass.isInstance(catalog)) {
         return catalogClass.cast(catalog);
      } else {
         throw new IllegalStateException("Unknown step catalog " + catalog + ", want: " + catalogClass);
      }
   }

   public BaseSequenceBuilder step(Step step) {
      steps.add(new ProvidedStepBuilder(step, this));
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
      other.steps.forEach(s -> s.addCopyTo(this));
   }

   public String name() {
      return parent.name();
   }

   public BaseSequenceBuilder insertBefore(StepBuilder step) {
      return insertWithOffset(step, 0);
   }

   public BaseSequenceBuilder insertAfter(StepBuilder step) {
      return insertWithOffset(step, 1);
   }

   private BaseSequenceBuilder insertWithOffset(StepBuilder step, int offset) {
      if (!step.canBeLocated()) {
         throw new IllegalStateException(step + " cannot be located as it does not support deep copy.");
      }
      for (int i = 0; i < steps.size(); ++i) {
         if (steps.get(i) == step) {
            StepInserter inserter = new StepInserter(this);
            steps.add(i + offset, inserter);
            return inserter;
         }
      }
      throw new NoSuchElementException("Not found: " + step);
   }

   protected List<Step> buildSteps(SerializableSupplier<Sequence> sequence) {
      return steps.stream().map(b -> b.build(sequence)).flatMap(List::stream).collect(Collectors.toList());
   }

   public Locator createLocator() {
      return new Locator() {
         @Override
         public StepBuilder step() {
            throw new UnsupportedOperationException();
         }

         @Override
         public BaseSequenceBuilder sequence() {
            return BaseSequenceBuilder.this;
         }

         @Override
         public ScenarioBuilder scenario() {
            return endSequence();
         }
      };
   }

   private static class StepInserter extends BaseSequenceBuilder implements StepBuilder {
      private StepInserter(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return buildSteps(sequence);
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
      }

      @Override
      public void addCopyTo(BaseSequenceBuilder newParent) {
         newParent.stepBuilder(new StepInserter(newParent));
      }

      @Override
      public boolean canBeLocated() {
         return true;
      }
   }

   private static class ProvidedStepBuilder implements StepBuilder {
      private final Step step;
      private final BaseSequenceBuilder parent;

      public ProvidedStepBuilder(Step step, BaseSequenceBuilder parent) {
         this.step = step;
         this.parent = parent;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(step);
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
      }

      @Override
      public void addCopyTo(BaseSequenceBuilder newParent) {
         newParent.stepBuilder(new ProvidedStepBuilder(step, newParent));
      }

      @Override
      public boolean canBeLocated() {
         return true;
      }
   }
}
