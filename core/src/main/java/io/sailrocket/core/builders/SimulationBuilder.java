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

import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Simulation;
import io.sailrocket.spi.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.spi.HttpBase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SimulationBuilder {

    private final BenchmarkBuilder benchmarkBuilder;
    private HttpBase http;
    private int connections = 1;
    private int concurrency = 1;
    private int threads = 1;
    private Map<String, PhaseBuilder> phaseBuilders = new HashMap<>();
    private long statisticsCollectionPeriod = 1000;

    SimulationBuilder(BenchmarkBuilder benchmarkBuilder) {
        this.benchmarkBuilder = benchmarkBuilder;
    }

    public BenchmarkBuilder endSimulation() {
        return benchmarkBuilder;
    }

    private SimulationBuilder apply(Consumer<SimulationBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public SimulationBuilder http(HttpBase http) {
        return apply(clone -> clone.http = http);
    }

    public SimulationBuilder http(HttpBuilder httpBuilder) {
        return apply(clone -> clone.http = httpBuilder.build());
    }

    public SimulationBuilder connections(int connections) {
        return apply(clone -> clone.connections = connections);
    }

    public SimulationBuilder concurrency(int concurrency) {
        return apply(clone -> clone.concurrency = concurrency);
    }

    public SimulationBuilder threads(int threads) {
        return apply(clone -> clone.threads = threads);
    }

    public PhaseBuilder.Discriminator addPhase(String name) {
        return new PhaseBuilder.Discriminator(this, name);
    }

    public Simulation build() {
        Collection<Phase> phases = new ArrayList<>();
        for (PhaseBuilder<?> builder : this.phaseBuilders.values()) {
            for (String dep : builder.startAfter) {
                if (!phaseBuilders.containsKey(dep)) {
                    throw new BenchmarkDefinitionException("Phase " + dep + " not defined");
                }
            }
            for (String dep : builder.startAfterStrict) {
                if (!phaseBuilders.containsKey(dep)) {
                    throw new BenchmarkDefinitionException("Phase " + dep + " not defined");
                }
            }
            phases.add(builder.build());
        }
        return new SimulationImpl(buildClientPoolFactory(), phases, buildTags(), statisticsCollectionPeriod);
    }

    private HttpClientPoolFactory buildClientPoolFactory() {
        return HttpClientProvider.netty.builder()
                       .threads(threads)
                       .ssl(http.baseUrl().protocol().secure())
                       .port(http.baseUrl().protocol().port())
                       .host(http.baseUrl().host())
                       .size(connections)
                       .concurrency(concurrency);
                       //TODO: need a way to specify protocol
                       //.protocol(http.baseUrl().protocol().version());
    }

    private Map<String, Object> buildTags() {
        Map<String, Object> tags = new HashMap<>();
        tags.put("url", http.baseUrl().toString());
        tags.put("protocol", http.baseUrl().protocol().version().toString());
        tags.put("maxQueue", concurrency);
        tags.put("connections", connections);
        tags.put("threads", threads);

        return tags;
    }

    void addPhase(String name, PhaseBuilder phaseBuilder) {
        if (phaseBuilders.containsKey(name)) {
            throw new IllegalArgumentException("Phase '" + name + "' already defined.");
        }
        phaseBuilders.put(name, phaseBuilder);
    }
}
