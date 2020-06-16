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

   public BaseSequenceBuilder step(SimpleBuilder builder) {
      steps.add(new SimpleAdapter(builder));
      return this;
   }

   // Calling this method step() would cause ambiguity with step(Step) defined through lambda
   public BaseSequenceBuilder stepBuilder(StepBuilder<?> stepBuilder) {
      steps.add(stepBuilder);
      return this;
   }

   public BaseSequenceBuilder end() {
      return parent;
   }

   public SequenceBuilder rootSequence() {
      return parent.rootSequence();
   }

   public ScenarioBuilder endSequence() {
      return rootSequence().endSequence();
   }

   @Override
   public void readFrom(BaseSequenceBuilder other) {
      assert steps.isEmpty();
      other.steps.forEach(s -> stepBuilder(s.copy()));
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

   public void prepareBuild() {
      // We need to make a defensive copy as prepareBuild() may trigger modifications
      new ArrayList<>(steps).forEach(stepBuilder -> {
         Locator.push(stepBuilder, BaseSequenceBuilder.this);
         stepBuilder.prepareBuild();
         Locator.pop();
      });
   }

   public List<Step> buildSteps() {
      return steps.stream().map(stepBuilder -> {
         Locator.push(stepBuilder, BaseSequenceBuilder.this);
         List<Step> steps = stepBuilder.build();
         Locator.pop();
         return steps;
      }).flatMap(List::stream).collect(Collectors.toList());
   }

   public int indexOf(StepBuilder<?> builder) {
      return steps.indexOf(builder);
   }

   public boolean isEmpty() {
      return steps.isEmpty();
   }

   /**
    * Simplified interface that works better with lambdas
    */
   @FunctionalInterface
   public interface SimpleBuilder {
      Step build();
   }

   private static class StepInserter extends BaseSequenceBuilder implements StepBuilder<StepInserter> {
      private StepInserter(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public void prepareBuild() {
         super.prepareBuild();
      }

      @Override
      public List<Step> build() {
         return buildSteps();
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

      @Override
      public ProvidedStepBuilder copy() {
         // This builder is immutable
         return this;
      }
   }

   public static class SimpleAdapter implements StepBuilder<SimpleAdapter> {
      private final SimpleBuilder builder;

      public SimpleAdapter(SimpleBuilder builder) {
         this.builder = builder;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(builder.build());
      }
   }
}
