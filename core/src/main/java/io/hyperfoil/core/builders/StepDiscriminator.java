package io.hyperfoil.core.builders;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.generators.RandomIntStep;
import io.hyperfoil.core.generators.RandomItemStep;
import io.hyperfoil.core.generators.TemplateStep;
import io.hyperfoil.core.steps.AwaitAllResponsesStep;
import io.hyperfoil.core.steps.AwaitConditionStep;
import io.hyperfoil.core.steps.AwaitDelayStep;
import io.hyperfoil.core.steps.AwaitIntStep;
import io.hyperfoil.core.steps.BreakSequenceStep;
import io.hyperfoil.core.steps.ForeachStep;
import io.hyperfoil.core.steps.HttpRequestStep;
import io.hyperfoil.core.steps.LoopStep;
import io.hyperfoil.core.steps.PollStep;
import io.hyperfoil.core.steps.ScheduleDelayStep;
import io.hyperfoil.core.steps.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.steps.StopwatchBeginStep;

/**
 * Helper class to gather well-known step builders
 */
public class StepDiscriminator {
   private final BaseSequenceBuilder parent;

   StepDiscriminator(BaseSequenceBuilder parent) {
      this.parent = parent;
   }

   // control steps

   public BreakSequenceStep.Builder breakSequence(Predicate<Session> condition) {
      return new BreakSequenceStep.Builder(parent, condition);
   }

   public BaseSequenceBuilder nextSequence(String name) {
      return parent.step(s -> {
         s.nextSequence(name);
         return true;
      });
   }

   public BaseSequenceBuilder loop(String counterVar, int repeats, String loopedSequence) {
      return parent.step(new LoopStep(counterVar, repeats, loopedSequence));
   }

   public ForeachStep.Builder foreach(String dataVar, String counterVar) {
      return new ForeachStep.Builder(parent, dataVar, counterVar);
   }

   public BaseSequenceBuilder stop() {
      return parent.step(s -> {
         s.stop();
         return true;
      });
   }

   // requests

   public HttpRequestStep.Builder httpRequest(HttpMethod method) {
      return new HttpRequestStep.Builder(parent, method);
   }

   public BaseSequenceBuilder awaitAllResponses() {
      return parent.step(new AwaitAllResponsesStep());
   }

   // timing

   public ScheduleDelayStep.Builder scheduleDelay(String key, long duration, TimeUnit timeUnit) {
      return new ScheduleDelayStep.Builder(parent, key, duration, timeUnit);
   }

   public BaseSequenceBuilder awaitDelay(String key) {
      return parent.step(new AwaitDelayStep(key));
   }

   public AwaitDelayStep.Builder awaitDelay() {
      return new AwaitDelayStep.Builder(parent);
   }

   public ScheduleDelayStep.Builder thinkTime(long duration, TimeUnit timeUnit) {
      // We will schedule two steps bound by an unique key
      Object key = new Object();
      // thinkTime should expose builder to support configurable duration randomization in the future
      ScheduleDelayStep.Builder delayBuilder = new ScheduleDelayStep.Builder(parent, key, duration, timeUnit).fromNow();
      parent.stepBuilder(delayBuilder);
      parent.step(new AwaitDelayStep(key));
      return delayBuilder;
   }

   public StopwatchBeginStep.Builder stopwatch() {
      return new StopwatchBeginStep.Builder(parent);
   }

   // general

   public BaseSequenceBuilder awaitCondition(Predicate<Session> condition) {
      return parent.step(new AwaitConditionStep(condition));
   }

   public AwaitIntStep.Builder awaitInt() {
      return new AwaitIntStep.Builder(parent);
   }

   public <T> PollStep.Builder<T> poll(Function<Session, T> provider, String intoVar) {
      return new PollStep.Builder<>(parent, provider, intoVar);
   }

   public <T> PollStep.Builder<T> poll(Supplier<T> supplier, String intoVar) {
      return new PollStep.Builder<>(parent, session -> supplier.get(), intoVar);
   }

   // generators

   public TemplateStep.Builder template() {
      return new TemplateStep.Builder(parent);
   }

   public RandomIntStep.Builder randomInt() {
      return new RandomIntStep.Builder(parent);
   }

   public RandomItemStep.Builder randomItem() {
      return new RandomItemStep.Builder(parent);
   }

   public ServiceLoadedBuilderProvider<StepBuilder> serviceLoaded() {
      return new ServiceLoadedBuilderProvider<>(StepBuilder.Factory.class, parent::stepBuilder);
   }
}
