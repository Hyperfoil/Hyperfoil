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

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.connection.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.core.builders.connection.HttpBase;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SimulationBuilder {

    private final BenchmarkBuilder benchmarkBuilder;
    private HttpBuilder http;
    private int connections = 1;
    private int concurrency = 1;
    private int threads = 1;
    private Map<String, PhaseBuilder<?>> phaseBuilders = new HashMap<>();
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

    public HttpBuilder http() {
        if (http == null) {
            http = new HttpBuilder(this);
        }
        return http;
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
        Collection<Phase> phases = phaseBuilders.values().stream()
              .flatMap(builder -> builder.build().stream()).collect(Collectors.toList());
        Set<String> phaseNames = phases.stream().map(Phase::name).collect(Collectors.toSet());
        for (Phase phase : phases) {
            checkDependencies(phase, phase.startAfter, phaseNames);
            checkDependencies(phase, phase.startAfterStrict, phaseNames);
            checkDependencies(phase, phase.terminateAfterStrict, phaseNames);
        }
        return new Simulation(buildClientPoolFactory(), phases, buildTags(), statisticsCollectionPeriod);
    }

    private void checkDependencies(Phase phase, Collection<String> references, Set<String> phaseNames) {
        for (String dep : references) {
            if (!phaseNames.contains(dep)) {
                String suggestion = phaseNames.stream()
                      .filter(name -> name.toLowerCase().startsWith(phase.name.toLowerCase())).findAny()
                      .map(name -> " Did you mean " + name + "?").orElse("");
                throw new BenchmarkDefinitionException("Phase " + dep + " referenced from " + phase.name() + " is not defined." + suggestion);
            }
        }
    }

    private HttpClientPoolFactory buildClientPoolFactory() {
        if (http == null) {
            // TODO this should be optional
            throw new BenchmarkDefinitionException("HTTP settings must be defined");
        }
        HttpBase http = this.http.build();
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
        HttpBase http = this.http.build();
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

    public SimulationBuilder statisticsCollectionPeriod(long statisticsCollectionPeriod) {
        this.statisticsCollectionPeriod = statisticsCollectionPeriod;
        return this;
    }
}
