package io.hyperfoil.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import io.hyperfoil.impl.StepCatalogFactory;

public abstract class BaseSequenceBuilder implements Rewritable<BaseSequenceBuilder> {
   private static final StepCatalogFactory sdf;

   protected final BaseSequenceBuilder parent;
   protected final List<StepBuilder<?>> steps = new ArrayList<>();

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
      steps.add(new ProvidedStepBuilder(step));
      return this;
   }

   // Calling this method step() would cause ambiguity with step(Step) defined through lambda
   public BaseSequenceBuilder stepBuilder(StepBuilder<?> stepBuilder) {
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
      Locator locator = createLocator();
      other.steps.forEach(s -> stepBuilder(s.copy(locator)));
   }

   public String name() {
      return parent.name();
   }

   public BaseSequenceBuilder insertBefore(Locator locator) {
      return insertWithOffset(locator, 0);
   }

   public BaseSequenceBuilder insertAfter(Locator locator) {
      return insertWithOffset(locator, 1);
   }

   private BaseSequenceBuilder insertWithOffset(Locator locator, int offset) {
      for (int i = 0; i < steps.size(); ++i) {
         if (steps.get(i) == locator.step()) {
            StepInserter inserter = new StepInserter(this);
            steps.add(i + offset, inserter);
            return inserter;
         }
      }
      throw new NoSuchElementException("Not found: " + locator.step());
   }

   protected List<Step> buildSteps() {
      return steps.stream().map(StepBuilder::build).flatMap(List::stream).collect(Collectors.toList());
   }

   public Locator createLocator() {
      return new Locator() {
         @Override
         public StepBuilder<?> step() {
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

   private static class StepInserter extends BaseSequenceBuilder implements StepBuilder<StepInserter> {
      private StepInserter(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build() {
         return buildSteps();
      }

      @Override
      public StepInserter setLocator(Locator locator) {
         steps.stream().forEach(s -> s.setLocator(locator));
         return this;
      }

      @Override
      public StepInserter copy(Locator locator) {
         StepInserter copy = new StepInserter(locator.sequence());
         steps.stream().map(s -> s.copy(locator)).forEach(copy::stepBuilder);
         return copy;
      }
   }

   private static class ProvidedStepBuilder implements StepBuilder<ProvidedStepBuilder> {
      private final Step step;

      public ProvidedStepBuilder(Step step) {
         this.step = step;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(step);
      }
   }
}
