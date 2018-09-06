/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.sailrocket.core.builders;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Session;
import io.sailrocket.api.Step;
import io.sailrocket.core.steps.AwaitAllResponsesStep;
import io.sailrocket.core.steps.AwaitDelayStep;
import io.sailrocket.core.steps.BreakSequenceStep;
import io.sailrocket.core.steps.LoopStep;
import io.sailrocket.core.steps.ScheduleDelayStep;
import io.sailrocket.core.steps.HttpRequestStep;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public interface StepBuilder {
   Step build();

   /**
    * Helper class to gather well-known step builders
    */
   class Discriminator {
      private final SequenceBuilder parent;

      Discriminator(SequenceBuilder parent) {
         this.parent = parent;
      }

      public BreakSequenceStep.Builder breakSequence(Predicate<Session> condition) {
         return new BreakSequenceStep.Builder(parent, condition);
      }

      public HttpRequestStep.Builder httpRequest(HttpMethod method) {
         return new HttpRequestStep.Builder(parent, method);
      }

      public SequenceBuilder awaitAllResponses() {
         parent.step(new AwaitAllResponsesStep());
         return parent;
      }

      public SequenceBuilder loop(String counterVar, int repeats, String loopedSequence) {
         parent.step(new LoopStep(counterVar, repeats, loopedSequence));
         return parent;
      }

      public ScheduleDelayStep.Builder scheduleDelay(String key, long duration, TimeUnit timeUnit) {
         return new ScheduleDelayStep.Builder(parent, key, duration, timeUnit);
      }

      public SequenceBuilder awaitDelay(String key) {
         parent.step(new AwaitDelayStep(key));
         return parent;
      }

      public SequenceBuilder thinkTime(long duration, TimeUnit timeUnit) {
         // We will schedule two steps bound by an unique key
         Object key = new Object();
         parent.step(new ScheduleDelayStep(key, ScheduleDelayStep.Type.FROM_NOW, duration, timeUnit));
         parent.step(new AwaitDelayStep(key));
         return parent;
      }
   }
}
