package io.sailrocket.core.builders;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Session;
import io.sailrocket.core.steps.AwaitAllResponsesStep;
import io.sailrocket.core.steps.AwaitConditionStep;
import io.sailrocket.core.steps.AwaitDelayStep;
import io.sailrocket.core.steps.BreakSequenceStep;
import io.sailrocket.core.steps.HttpRequestStep;
import io.sailrocket.core.steps.LoopStep;
import io.sailrocket.core.steps.ScheduleDelayStep;

/**
 * Helper class to gather well-known step builders
 */
public class StepDiscriminator {
   private final SequenceBuilder parent;

   StepDiscriminator(SequenceBuilder parent) {
      this.parent = parent;
   }

   // control steps

   public BreakSequenceStep.Builder breakSequence(Predicate<Session> condition) {
      return new BreakSequenceStep.Builder(parent, condition);
   }

   public SequenceBuilder nextSequence(String name) {
      parent.step(s -> s.nextSequence(name));
      return parent;
   }

   public SequenceBuilder loop(String counterVar, int repeats, String loopedSequence) {
      parent.step(new LoopStep(counterVar, repeats, loopedSequence));
      return parent;
   }

   public SequenceBuilder stop() {
      parent.step(Session::stop);
      return parent;
   }

   // requests

   public HttpRequestStep.Builder httpRequest(HttpMethod method) {
      return new HttpRequestStep.Builder(parent, method);
   }

   public SequenceBuilder awaitAllResponses() {
      parent.step(new AwaitAllResponsesStep());
      return parent;
   }

   // timing

   public ScheduleDelayStep.Builder scheduleDelay(String key, long duration, TimeUnit timeUnit) {
      return new ScheduleDelayStep.Builder(parent, key, duration, timeUnit);
   }

   public SequenceBuilder awaitDelay(String key) {
      parent.step(new AwaitDelayStep(key));
      return parent;
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

   // general

   public SequenceBuilder awaitCondition(Predicate<Session> condition) {
      parent.step(new AwaitConditionStep(condition));
      return parent;
   }

}
