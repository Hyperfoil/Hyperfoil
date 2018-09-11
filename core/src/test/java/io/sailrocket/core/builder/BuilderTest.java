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

import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.impl.SimulationImpl;

import org.junit.Test;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BuilderTest {

    @Test
    public void testBuilders() {

        SimulationImpl simulation = simulationBuilder()
                .http(httpBuilder().baseUrl("http://localhost:8080"))
                .concurrency(10)
                .connections(1)
                .addPhase("foo").always(1)
                    .duration("3s")
                    .scenario(scenarioBuilder()
                        .initialSequence(sequenceBuilder()
                                .step().httpRequest(HttpMethod.GET)
                                        .path("foo")
                                        .endStep()
                                .step().awaitAllResponses()
                                .end()
                        )
                    )
                    .endPhase()
                .build();

        assertEquals("http://localhost:8080/", simulation.tags().getString("url"));
        assertEquals(10, simulation.tags().getInteger("maxQueue").intValue());
        assertEquals(1, simulation.tags().getInteger("connections").intValue());
        assertEquals(101, simulation.tags().getInteger("rate").intValue());
        assertEquals(1, simulation.phases().size());
        assertEquals(3000000000L, simulation.phases().stream().findFirst().get().duration());

        BenchmarkImpl benchmark =
                BenchmarkBuilder.builder()
                        .name("Test Benchmark")
                        .simulation(simulation)
                        .build();
    }
}
