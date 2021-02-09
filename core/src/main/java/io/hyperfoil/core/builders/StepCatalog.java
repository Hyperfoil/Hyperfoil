package io.hyperfoil.core.builders;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.generators.RandomCsvRowStep;
import io.hyperfoil.core.generators.RandomIntStep;
import io.hyperfoil.core.generators.RandomItemStep;
import io.hyperfoil.core.generators.TemplateStep;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.AwaitConditionStep;
import io.hyperfoil.core.steps.AwaitDelayStep;
import io.hyperfoil.core.steps.AwaitIntStep;
import io.hyperfoil.core.steps.AwaitVarStep;
import io.hyperfoil.core.steps.BreakSequenceStep;
import io.hyperfoil.core.steps.ForeachStep;
import io.hyperfoil.core.steps.JsonStep;
import io.hyperfoil.core.steps.LogStep;
import io.hyperfoil.core.steps.LoopStep;
import io.hyperfoil.core.steps.NextSequenceStep;
import io.hyperfoil.core.steps.PollStep;
import io.hyperfoil.core.steps.PullSharedMapStep;
import io.hyperfoil.core.steps.PushSharedMapStep;
import io.hyperfoil.core.steps.ScheduleDelayStep;
import io.hyperfoil.core.steps.StopStep;
import io.hyperfoil.core.steps.StopwatchBeginStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.function.SerializablePredicate;
import io.hyperfoil.impl.StepCatalogFactory;

/**
 * Helper class to gather well-known step builders
 */
public class StepCatalog implements Step.Catalog, ServiceLoadedBuilderProvider.Owner<StepBuilder> {
   public static Class<StepCatalog> SC = StepCatalog.class;

   protected final BaseSequenceBuilder parent;

   protected StepCatalog(BaseSequenceBuilder parent) {
      this.parent = parent;
   }

   // control steps

   public BreakSequenceStep.Builder breakSequence() {
      return new BreakSequenceStep.Builder().addTo(parent);
   }

   /**
    * Schedules a new sequence instance to be executed.
    *
    * @param name Name of the instantiated sequence.
    * @return This sequence.
    */
   public BaseSequenceBuilder nextSequence(String name) {
      return parent.step(new NextSequenceStep(name));
   }

   public LoopStep.Builder loop(String counterVar, int repeats) {
      // We don't return .steps() because that would be more prone to incorrect nesting
      LoopStep.Builder builder = new LoopStep.Builder(parent).counterVar(counterVar).repeats(repeats);
      parent.stepBuilder(builder);
      return builder;
   }

   public ForeachStep.Builder foreach() {
      return new ForeachStep.Builder().addTo(parent);
   }

   /**
    * Immediately stop the user session (break all running sequences).
    *
    * @return This sequence.
    */
   public BaseSequenceBuilder stop() {
      return parent.step(new StopStep());
   }

   // timing

   /**
    * Define a point in future until which we should wait. Do not wait yet.
    *
    * @param key      Identifier.
    * @param duration Delay duration.
    * @param timeUnit Time unit.
    * @return Builder.
    */
   public ScheduleDelayStep.Builder scheduleDelay(String key, long duration, TimeUnit timeUnit) {
      return new ScheduleDelayStep.Builder().addTo(parent).key(key).duration(duration, timeUnit);
   }

   /**
    * Block this sequence until referenced delay point.
    *
    * @param key Delay point created in <code>scheduleDelay.key</code>.
    * @return This sequence.
    */
   public BaseSequenceBuilder awaitDelay(String key) {
      return parent.step(() -> new AwaitDelayStep(SessionFactory.access(key)));
   }

   public AwaitDelayStep.Builder awaitDelay() {
      return new AwaitDelayStep.Builder().addTo(parent);
   }

   /**
    * Block the current sequence for specified duration.
    *
    * @param duration Delay duration.
    * @param timeUnit Time unit.
    * @return Builder.
    */
   public ScheduleDelayStep.Builder thinkTime(long duration, TimeUnit timeUnit) {
      return thinkTime().duration(duration, timeUnit);
   }

   /**
    * Block the current sequence for specified duration.
    *
    * @return Builder.
    */
   public ScheduleDelayStep.ThinkTimeBuilder thinkTime() {
      return new ScheduleDelayStep.ThinkTimeBuilder().addTo(parent);
   }

   public StopwatchBeginStep.Builder stopwatch() {
      StopwatchBeginStep.Builder builder = new StopwatchBeginStep.Builder(parent);
      parent.stepBuilder(builder);
      return builder;
   }

   // general

   /**
    * Block current sequence until condition becomes true.
    *
    * @param condition Condition predicate.
    * @return This sequence.
    */
   public BaseSequenceBuilder awaitCondition(SerializablePredicate<Session> condition) {
      return parent.step(new AwaitConditionStep(condition));
   }

   /**
    * Block current sequence until condition becomes true.
    *
    * @return Builder.
    */
   public AwaitIntStep.Builder awaitInt() {
      AwaitIntStep.Builder builder = new AwaitIntStep.Builder(parent);
      parent.stepBuilder(builder);
      return builder;
   }

   /**
    * Block current sequence until this variable gets set/unset.
    *
    * @param var Variable name or <code>!variable</code> if we are waiting for it to be unset.
    * @return This sequence.
    */
   public BaseSequenceBuilder awaitVar(String var) {
      return parent.stepBuilder(new AwaitVarStep.Builder().var(var));
   }

   public BaseSequenceBuilder action(Action.Builder builder) {
      return parent.stepBuilder(new StepBuilder.ActionAdapter(builder));
   }

   public <T> PollStep.Builder<T> poll(SerializableFunction<Session, T> provider, String intoVar) {
      return new PollStep.Builder<>(provider, intoVar).addTo(parent);
   }

   public <T> PollStep.Builder<T> poll(Supplier<T> supplier, String intoVar) {
      return new PollStep.Builder<>(session -> supplier.get(), intoVar).addTo(parent);
   }

   // generators

   public TemplateStep.Builder template() {
      return new TemplateStep.Builder().addTo(parent);
   }

   public RandomIntStep.Builder randomInt() {
      return new RandomIntStep.Builder().addTo(parent);
   }

   /**
    * Stores random (linearly distributed) integer into session variable.
    *
    * @param rangeToVar Use <code>var &lt;- min..max</code>
    * @return Builder.
    */
   public RandomIntStep.Builder randomInt(String rangeToVar) {
      return randomInt().init(rangeToVar);
   }

   public RandomItemStep.Builder randomItem() {
      return new RandomItemStep.Builder().addTo(parent);
   }

   /**
    * Stores random item from a list or array into session variable.
    *
    * @param toFrom Use <code>var &lt;- arrayVariable</code>
    * @return Builder.
    */
   public RandomItemStep.Builder randomItem(String toFrom) {
      return randomItem().init(toFrom);
   }

   public RandomCsvRowStep.Builder randomCsvRow() {
      return new RandomCsvRowStep.Builder().addTo(parent);
   }

   @Override
   public ServiceLoadedBuilderProvider<StepBuilder> serviceLoaded() {
      return new ServiceLoadedBuilderProvider<>(StepBuilder.class, parent::stepBuilder, parent);
   }

   // data

   public JsonStep.Builder json() {
      return new JsonStep.Builder().addTo(parent);
   }

   /**
    * Move values from a map shared across all sessions using the same executor into session variables.
    *
    * @return Builder.
    */
   public PullSharedMapStep.Builder pullSharedMap() {
      return new PullSharedMapStep.Builder().addTo(parent);
   }

   /**
    * Store values from session variables into a map shared across all sessions using the same executor into session variables.
    *
    * @return Builder.
    */
   public PushSharedMapStep.Builder pushSharedMap() {
      return new PushSharedMapStep.Builder().addTo(parent);
   }

   // utility

   public LogStep.Builder log() {
      return new LogStep.Builder().addTo(parent);
   }

   @MetaInfServices(StepCatalogFactory.class)
   public static class Factory implements StepCatalogFactory {
      @Override
      public Class<? extends Step.Catalog> clazz() {
         return StepCatalog.class;
      }

      @Override
      public Step.Catalog create(BaseSequenceBuilder sequenceBuilder) {
         return new StepCatalog(sequenceBuilder);
      }
   }
}
