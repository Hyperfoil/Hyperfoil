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

import io.sailrocket.api.Phase;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.spi.HttpBase;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SimulationBuilder {

    private HttpBase http;
    private int connections = 1;
    private int concurrency = 1;
    private int threads = 1;
    private Map<String, PhaseBuilder> phaseBuilders = new HashMap<>();
    private Set<PhaseBuilder> activeBuilders = new LinkedHashSet<>();
    private Map<String, Phase> phases = new HashMap<>();
    //also support an endpoint for a simple benchmark

    private SimulationBuilder() {
    }

    public static SimulationBuilder simulationBuilder() {
        return new SimulationBuilder();
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

    public SimulationImpl build() {
        for (PhaseBuilder builder : this.phaseBuilders.values()) {
            // phase might be created by its dependency
            if (phases.containsKey(builder.name)) continue;
            if (!activeBuilders.add(builder)) {
                throw new IllegalArgumentException("Phase builder '" + builder.name + "' already active");
            }
            try {
                phases.put(builder.name, builder.build());
            } finally {
                activeBuilders.remove(builder);
            }
        }
        assert activeBuilders.isEmpty();
        Collection<Phase> phases = this.phases.values();
        // prevent re-use of phases
        this.phases = new HashMap<>();
        return new SimulationImpl(buildClientPoolFactory(), phases, buildTags());
    }

    private HttpClientPoolFactory buildClientPoolFactory() {
        return HttpClientProvider.vertx.builder()
                       .threads(threads)
                       .ssl(http.baseUrl().protocol().secure())
                       .port(http.baseUrl().protocol().port())
                       .host(http.baseUrl().host())
                       .size(connections)
                       .concurrency(concurrency);
                       //TODO: need a way to specify protocol
                       //.protocol(http.baseUrl().protocol().version());
    }

    private JsonObject buildTags() {
        JsonObject tags = new JsonObject();
        tags.put("url", http.baseUrl().toString());
        tags.put("protocol", http.baseUrl().protocol().version().toString());
        tags.put("maxQueue", concurrency);
        tags.put("connections", connections);
        tags.put("threads", threads);

        return tags;
    }

    Collection<Phase> getPhases(Collection<String> phases) {
        return phases.stream().map(name -> {
            Phase phase = this.phases.get(name);
            if (phase != null) {
                return phase;
            }
            PhaseBuilder builder = phaseBuilders.get(name);
            if (builder == null) {
                throw new IllegalArgumentException("There's no phase '" + name + "'");
            }
            if (!activeBuilders.add(builder)) {
                throw new IllegalArgumentException("Recursion in phase dependencies requesting '" + name + "', chain is " + activeBuilders);
            }
            try {
                phase = builder.build();
            } finally {
                activeBuilders.remove(builder);
            }
            this.phases.put(builder.name, phase);
            return phase;
        }).collect(Collectors.toList());
    }

    void addPhase(String name, PhaseBuilder phaseBuilder) {
        if (phaseBuilders.containsKey(name)) {
            throw new IllegalArgumentException("Phase '" + name + "' already defined.");
        }
        phaseBuilders.put(name, phaseBuilder);
    }
}
