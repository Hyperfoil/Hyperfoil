package io.sailrocket.core.builders;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.sailrocket.api.config.Step;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.steps.AwaitAllResponsesStep;
import io.sailrocket.core.steps.AwaitConditionStep;
import io.sailrocket.core.steps.AwaitDelayStep;
import io.sailrocket.core.steps.AwaitIntStep;
import io.sailrocket.core.steps.BreakSequenceStep;
import io.sailrocket.core.steps.ForeachStep;
import io.sailrocket.core.steps.HttpRequestStep;
import io.sailrocket.core.steps.LoopStep;
import io.sailrocket.core.steps.PollStep;
import io.sailrocket.core.steps.ScheduleDelayStep;
import io.sailrocket.core.steps.ServiceLoadedBuilderProvider;
import io.sailrocket.core.steps.StopwatchBeginStep;

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
      parent.step(delayBuilder);
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

   public ServiceLoadedBuilderProvider<List<Step>> serviceLoaded() {
      return new ServiceLoadedBuilderProvider<>(Step.BuilderFactory.class, steps -> steps.forEach(parent::step));
   }
}
