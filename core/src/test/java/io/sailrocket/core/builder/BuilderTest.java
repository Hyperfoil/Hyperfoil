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

package io.sailrocket.core.builder;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.builders.BenchmarkBuilder;

import org.junit.Test;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BuilderTest {

    @Test
    public void testBuilders() {

        Benchmark benchmark =
              BenchmarkBuilder.builder()
                    .name("Test Benchmark")
                    .simulation()
                        .http(httpBuilder().baseUrl("http://localhost:8080"))
                        .concurrency(10)
                        .connections(1)
                        .addPhase("foo").always(1)
                            .duration("3s")
                            .scenario()
                                .initialSequence("foo")
                                    .step().httpRequest(HttpMethod.GET)
                                            .path("foo")
                                            .endStep()
                                    .step().awaitAllResponses()
                                    .end()
                                .endSequence()
                            .endScenario()
                        .endPhase()
                    .endSimulation()
                    .build();

        Simulation simulation = benchmark.simulation();

        assertEquals("http://localhost:8080/", simulation.tags().get("url"));
        assertEquals(10, simulation.tags().get("maxQueue"));
        assertEquals(1, simulation.tags().get("connections"));
        assertEquals(1, simulation.phases().size());
        assertEquals(3000L, simulation.phases().stream().findFirst().get().duration());


    }
}
