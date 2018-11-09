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
import io.sailrocket.api.config.Simulation;
import io.sailrocket.api.config.Http;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SimulationBuilder {

    private final BenchmarkBuilder benchmarkBuilder;
    private HttpBuilder defaultHttp;
    private Map<String, HttpBuilder> httpMap = new HashMap<>();
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
        if (defaultHttp == null) {
            defaultHttp = new HttpBuilder(this);
        }
        return defaultHttp;
    }

    public HttpBuilder http(String baseUrl) {
        return httpMap.computeIfAbsent(Objects.requireNonNull(baseUrl), url -> new HttpBuilder(this).baseUrl(url));
    }

    public SimulationBuilder threads(int threads) {
        return apply(clone -> clone.threads = threads);
    }

    public PhaseBuilder.Discriminator addPhase(String name) {
        return new PhaseBuilder.Discriminator(this, name);
    }

    public Simulation build() {
        if (defaultHttp == null) {
            if (httpMap.isEmpty()) {
                // may be removed in the future when we define more than HTTP connections
                throw new BenchmarkDefinitionException("No default HTTP target set!");
            } else if (httpMap.size() == 1) {
                defaultHttp = httpMap.values().iterator().next();
            } else {
                // Validate that base url is always set in steps
            }
        } else {
            if (httpMap.containsKey(defaultHttp.baseUrl())) {
                throw new BenchmarkDefinitionException("Ambiguous HTTP definition for "
                      + defaultHttp.baseUrl() + ": defined both as default and non-default");
            }
            httpMap.put(defaultHttp.baseUrl(), defaultHttp);
        }

        Map<String, Http> http = httpMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build(entry.getValue() == defaultHttp)));

        Collection<Phase> phases = phaseBuilders.values().stream()
              .flatMap(builder -> builder.build().stream()).collect(Collectors.toList());
        Set<String> phaseNames = phases.stream().map(Phase::name).collect(Collectors.toSet());
        for (Phase phase : phases) {
            checkDependencies(phase, phase.startAfter, phaseNames);
            checkDependencies(phase, phase.startAfterStrict, phaseNames);
            checkDependencies(phase, phase.terminateAfterStrict, phaseNames);
        }
        Map<String, Object> tags = new HashMap<>();
        if (defaultHttp != null) {
            Http defaultHttp = this.defaultHttp.build(true);
            tags.put("url", defaultHttp.baseUrl().toString());
            tags.put("protocol", defaultHttp.baseUrl().protocol().scheme);
        }
        tags.put("threads", threads);

        return new Simulation(threads, http, phases, tags, statisticsCollectionPeriod);
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

    Collection<PhaseBuilder<?>> phases() {
        return phaseBuilders.values();
    }

    public boolean validateBaseUrl(String baseUrl) {
        return baseUrl == null && defaultHttp != null || httpMap.containsKey(baseUrl);
    }

    public HttpBuilder decoupledHttp() {
        return new HttpBuilder(this);
    }

    public void addHttp(HttpBuilder builder) {
        if (builder.baseUrl() == null) {
            throw new BenchmarkDefinitionException("Missing baseUrl!");
        }
        if (httpMap.putIfAbsent(builder.baseUrl(), builder) != null) {
            throw new BenchmarkDefinitionException("HTTP configuration for " + builder.baseUrl() + " already present!");
        }
    }
}
