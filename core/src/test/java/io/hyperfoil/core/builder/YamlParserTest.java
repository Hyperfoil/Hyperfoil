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
package io.hyperfoil.core.builder;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.http.StatusValidator;
import io.hyperfoil.core.extractors.RangeStatusValidator;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.steps.AwaitIntStep;
import io.hyperfoil.core.steps.HttpRequestStep;
import io.hyperfoil.core.steps.HttpRequestStepUtil;
import io.hyperfoil.core.steps.NoopStep;
import io.hyperfoil.core.steps.ScheduleDelayStep;

import org.assertj.core.api.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.Assert.fail;

public class YamlParserTest {
    @Test
    public void testSimpleYaml() {
        Benchmark benchmark = buildBenchmark("scenarios/simple.yaml");
        assertThat(benchmark.name()).isEqualTo("simple benchmark");
        Phase[] phases = benchmark.simulation().phases().toArray(new Phase[0]);
        assertThat(phases.length).isEqualTo(3);
        Sequence[] sequences = phases[0].scenario().sequences();
        assertThat(sequences.length).isEqualTo(1);
        Step[] steps = sequences[0].steps();
        assertThat(steps.length).isEqualTo(5);
        assertThat(steps[0]).isInstanceOf(HttpRequestStep.class);
        assertThat(steps[1]).isInstanceOf(HttpRequestStep.class);
        assertThat(steps[2]).isInstanceOf(NoopStep.class);
        assertThat(steps[3]).isInstanceOf(AwaitIntStep.class);
        assertThat(steps[4]).isInstanceOf(ScheduleDelayStep.class);
    }

    @Test
    public void testComplexYaml() {
        Benchmark benchmark = buildBenchmark("scenarios/complex.yaml");
        assertThat(benchmark.name()).isEqualTo("complex benchmark");
        assertThat(benchmark.agents().length).isEqualTo(3);

        double sumWeights = 0.2 + 0.8 + 0.1 + 1;
        assertThat(phase(benchmark, "steadyState/invalidRegistration", Phase.ConstantPerSec.class).usersPerSec)
              .isCloseTo(100.0 / 3 / sumWeights * 0.2, withPercentage(1));
        assertThat(phase(benchmark, "steadyState/validRegistration", Phase.ConstantPerSec.class).usersPerSec)
              .isCloseTo(100.0 / 3 / sumWeights * 0.8, withPercentage(1));
        assertThat(phase(benchmark, "steadyState/unregister", Phase.ConstantPerSec.class).usersPerSec)
              .isCloseTo(100.0 / 3 / sumWeights * 0.1, withPercentage(1));
        assertThat(phase(benchmark, "steadyState/viewUser", Phase.ConstantPerSec.class).usersPerSec)
              .isCloseTo(100.0 / 3 / sumWeights * 1.0, withPercentage(1));
        assertThat(benchmark.simulation().phases().stream()
              .filter(p -> p instanceof Phase.ConstantPerSec)
              .mapToDouble(p -> ((Phase.ConstantPerSec) p).usersPerSec)
              .sum()).isCloseTo(100.0 / 3, withPercentage(1));
    }

    private <T extends Phase> T phase(Benchmark benchmark, String name, Class<T> type) {
        Phase phase = benchmark.simulation().phases().stream()
              .filter(p -> p.name().equals(name)).findFirst().get();
        assertThat(phase).isInstanceOf(type);
        return (T) phase;
    }

    @Test
    public void testIterationYaml() {
        Benchmark benchmark = buildBenchmark("scenarios/iteration.yaml");
        assertThat(benchmark.name()).isEqualTo("iteration benchmark");
    }

    @Test
    public void testAwaitDelayYaml() {
        Benchmark benchmark = buildBenchmark("scenarios/awaitDelay.yaml");
        assertThat(benchmark.name()).isEqualTo("await delay benchmark");
    }

    @Test
    public void testGeneratorsYaml() {
        buildBenchmark("scenarios/generators.yaml");
    }

    @Test
    public void testHttpRequestYaml() {
        Benchmark benchmark = buildBenchmark("scenarios/httpRequest.yaml");
        Phase testPhase = benchmark.simulation().phases().iterator().next();
        Sequence testSequence = testPhase.scenario().sequences()[0];
        Iterator<Step> iterator = Arrays.asList(testSequence.steps()).iterator();

        HttpRequestStep request1 = (HttpRequestStep) iterator.next();
        StatusValidator[] statusValidators1 = HttpRequestStepUtil.statusValidators(request1);
        assertThat(statusValidators1).isNotNull().hasSize(1);
        assertCondition((RangeStatusValidator) statusValidators1[0], v -> v.min == 200);
        assertCondition((RangeStatusValidator) statusValidators1[0], v -> v.max == 299);

        HttpRequestStep request2 = (HttpRequestStep) iterator.next();
        StatusValidator[] statusValidators2 = HttpRequestStepUtil.statusValidators(request2);
        assertThat(statusValidators2).isNotNull().hasSize(1);
        assertCondition((RangeStatusValidator) statusValidators2[0], v -> v.min == 201);
        assertCondition((RangeStatusValidator) statusValidators2[0], v -> v.max == 259);
    }

    private <T> void assertCondition(T object, Predicate<T> predicate) {
        assertThat(object).has(new Condition<>(predicate, ""));
    }

    private Benchmark buildBenchmark(String s) {
        return buildBenchmark(this.getClass().getClassLoader().getResourceAsStream(s));
    }

    private Benchmark buildBenchmark(InputStream inputStream){
        if (inputStream == null)
            fail("Could not find benchmark configuration");

        try {
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(inputStream);
            Assert.assertNotNull(benchmark);
            return benchmark;
        } catch (ParserException | IOException e) {
            throw new AssertionError("Error occurred during parsing", e);
        }
    }
}
