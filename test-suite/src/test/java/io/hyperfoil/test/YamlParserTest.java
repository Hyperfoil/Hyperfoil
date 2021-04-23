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
 */
package io.hyperfoil.test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.http.config.HttpPluginConfig;
import io.hyperfoil.http.handlers.RangeStatusValidator;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.steps.AwaitIntStep;
import io.hyperfoil.core.steps.NoopStep;
import io.hyperfoil.core.steps.ScheduleDelayStep;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.steps.SendHttpRequestStep;
import io.hyperfoil.http.steps.HttpRequestStepUtil;
import io.hyperfoil.http.steps.PrepareHttpRequestStep;

import org.assertj.core.api.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.Assert.fail;

public class YamlParserTest {
   @Test
   public void testSimpleYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/simple.hf.yaml");
      assertThat(benchmark.name()).isEqualTo("simple benchmark");
      Phase[] phases = benchmark.phases().toArray(new Phase[0]);
      assertThat(phases.length).isEqualTo(3);
      for (Phase p : phases) {
         Sequence[] sequences = p.scenario().sequences();
         assertThat(sequences.length).isEqualTo(1);
         Step[] steps = sequences[0].steps();
         assertThat(steps.length).isEqualTo(7);
         assertThat(steps[0]).isInstanceOf(PrepareHttpRequestStep.class);
         assertThat(steps[1]).isInstanceOf(SendHttpRequestStep.class);
         assertThat(steps[2]).isInstanceOf(PrepareHttpRequestStep.class);
         assertThat(steps[3]).isInstanceOf(SendHttpRequestStep.class);
         assertThat(steps[4]).isInstanceOf(NoopStep.class);
         assertThat(steps[5]).isInstanceOf(AwaitIntStep.class);
         assertThat(steps[6]).isInstanceOf(ScheduleDelayStep.class);
      }
   }

   @Test
   public void testComplexYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/complex.hf.yaml");
      assertThat(benchmark.name()).isEqualTo("complex benchmark");
      assertThat(benchmark.agents().length).isEqualTo(3);

      double sumWeights = 0.2 + 0.8 + 0.1 + 1;
      assertThat(model(benchmark, "steadyState/invalidRegistration", Model.ConstantRate.class).usersPerSec)
            .isCloseTo(100.0 / sumWeights * 0.2, withPercentage(1));
      assertThat(model(benchmark, "steadyState/validRegistration", Model.ConstantRate.class).usersPerSec)
            .isCloseTo(100.0 / sumWeights * 0.8, withPercentage(1));
      assertThat(model(benchmark, "steadyState/unregister", Model.ConstantRate.class).usersPerSec)
            .isCloseTo(100.0 / sumWeights * 0.1, withPercentage(1));
      assertThat(model(benchmark, "steadyState/viewUser", Model.ConstantRate.class).usersPerSec)
            .isCloseTo(100.0 / sumWeights * 1.0, withPercentage(1));
      assertThat(benchmark.phases().stream()
            .filter(p -> p.model instanceof Model.ConstantRate)
            .mapToDouble(p -> ((Model.ConstantRate) p.model).usersPerSec)
            .sum()).isCloseTo(100.0, withPercentage(1));
   }

   private <M extends Model> M model(Benchmark benchmark, String name, Class<M> type) {
      Model model = benchmark.phases().stream()
            .filter(p -> p.name().equals(name)).map(p -> p.model).findFirst().orElseThrow(AssertionError::new);
      assertThat(model).isInstanceOf(type);
      return type.cast(model);
   }

   @Test
   public void testShortcutYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/shortcut.hf.yaml");
      assertThat(benchmark.name()).isEqualTo("shortcut benchmark");
      assertThat(benchmark.phases().size()).isEqualTo(1);
      Phase phase = benchmark.phases().stream().findFirst().orElseThrow(AssertionError::new);
      assertThat(phase.name()).isEqualTo("main");
      assertThat(phase.duration()).isEqualTo(3000);
      assertThat(phase.maxDuration()).isEqualTo(5000);
      assertThat(((Model.ConstantRate) phase.model).usersPerSec).isEqualTo(100);
      assertThat(((Model.ConstantRate) phase.model).maxSessions).isEqualTo(1234);
      assertThat(phase.scenario().initialSequences().length).isEqualTo(1);
   }

   @Test
   public void testIterationYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/iteration.hf.yaml");
      assertThat(benchmark.name()).isEqualTo("iteration benchmark");
   }

   @Test
   public void testAwaitDelayYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/awaitDelay.hf.yaml");
      assertThat(benchmark.name()).isEqualTo("await delay benchmark");
   }

   @Test
   public void testGeneratorsYaml() {
      buildBenchmark("scenarios/generators.hf.yaml");
   }

   @Test
   public void testHttpRequestYaml() {
      Benchmark benchmark = buildBenchmark("scenarios/httpRequest.hf.yaml");
      Phase testPhase = benchmark.phases().iterator().next();
      Sequence testSequence = testPhase.scenario().sequences()[0];
      Iterator<Step> iterator = Arrays.asList(testSequence.steps()).iterator();

      PrepareHttpRequestStep request1 = next(PrepareHttpRequestStep.class, iterator);
      StatusHandler[] statusHandlers1 = HttpRequestStepUtil.statusHandlers(request1);
      assertThat(statusHandlers1).isNotNull().hasSize(1);
      assertCondition((RangeStatusValidator) statusHandlers1[0], v -> v.min == 200);
      assertCondition((RangeStatusValidator) statusHandlers1[0], v -> v.max == 299);

      PrepareHttpRequestStep request2 = next(PrepareHttpRequestStep.class, iterator);
      StatusHandler[] statusHandlers2 = HttpRequestStepUtil.statusHandlers(request2);
      assertThat(statusHandlers2).isNotNull().hasSize(2);
      assertCondition((RangeStatusValidator) statusHandlers2[0], v -> v.min == 201);
      assertCondition((RangeStatusValidator) statusHandlers2[0], v -> v.max == 259);
      assertCondition((RangeStatusValidator) statusHandlers2[1], v -> v.min == 200);
      assertCondition((RangeStatusValidator) statusHandlers2[1], v -> v.max == 210);
   }

   @Test
   public void testAgents1() {
      Benchmark benchmark = buildBenchmark("scenarios/agents1.hf.yaml");
      assertThat(benchmark.agents().length).isEqualTo(2);
   }

   @Test
   public void testAgents2() {
      Benchmark benchmark = buildBenchmark("scenarios/agents2.hf.yaml");
      assertThat(benchmark.agents().length).isEqualTo(3);
   }

   @Test
   public void testStaircase() {
      Benchmark benchmark = buildBenchmark("scenarios/staircase.hf.yaml");
      assertThat(benchmark.phases().stream().map(p -> p.model).filter(Model.RampRate.class::isInstance).count()).isEqualTo(3);
      assertThat(benchmark.phases().stream().map(p -> p.model).filter(Model.ConstantRate.class::isInstance).count()).isEqualTo(3);
      for (Phase phase : benchmark.phases()) {
         if (phase.model instanceof Model.Noop) {
            continue;
         }
         assertThat(phase.scenario.initialSequences().length).isEqualTo(1);
      }
   }

   @Test
   public void testMutualTls() {
      Benchmark benchmark = buildBenchmark("scenarios/mutualTls.hf.yaml");
      assertThat(benchmark.plugin(HttpPluginConfig.class).defaultHttp().keyManager().password()).isEqualTo("foobar");
      assertThat(benchmark.plugin(HttpPluginConfig.class).defaultHttp().trustManager().storeType()).isEqualTo("FOO");
   }

   @Test
   public void testHooks() {
      Benchmark benchmark = buildBenchmark("scenarios/hooks.hf.yaml");
      assertThat(benchmark.preHooks().size()).isEqualTo(2);
      assertThat(benchmark.postHooks().size()).isEqualTo(1);
   }

   @Test
   public void testSpecialVars() {
      Benchmark benchmark = buildBenchmark("scenarios/specialvars.hf.yaml");
      assertThat(benchmark.phases().size()).isEqualTo(10 + 1 /* one noop */);
   }

   @Test
   public void testLoop() {
      buildBenchmark("scenarios/loop.hf.yaml");
   }

   private <T extends Step> T next(Class<T> stepClass, Iterator<Step> iterator) {
      while (iterator.hasNext()) {
         Step step = iterator.next();
         if (stepClass.isInstance(step)) {
            return stepClass.cast(step);
         }
      }
      throw new NoSuchElementException();
   }

   private <T> void assertCondition(T object, Predicate<T> predicate) {
      assertThat(object).has(new Condition<>(predicate, ""));
   }

   private Benchmark buildBenchmark(String s) {
      ClassLoader classLoader = this.getClass().getClassLoader();
      return buildBenchmark(classLoader.getResourceAsStream(s));
   }

   private Benchmark buildBenchmark(InputStream inputStream) {
      if (inputStream == null)
         fail("Could not find benchmark configuration");

      try {
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(inputStream, TestUtil.benchmarkData());
         Assert.assertNotNull(benchmark);
         try {
            byte[] bytes = Util.serialize(benchmark);
            assertThat(bytes).isNotNull();
         } catch (IOException e) {
            throw new AssertionError(e);
         }
         return benchmark;
      } catch (ParserException | IOException e) {
         throw new AssertionError("Error occurred during parsing", e);
      }
   }
}
