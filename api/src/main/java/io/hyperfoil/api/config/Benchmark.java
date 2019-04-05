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
package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A benchmark is a collection of simulation, user,
 * SLA and scaling strategy (Ramp up, Steady State, Ramp Down, steady state variance)
 * that are to be run against the target environment.
 */
public class Benchmark implements Serializable {

    protected final String name;
    protected final String originalSource;
    protected final Host[] agents;
    private final int threads;
    private final Ergonomics ergonomics;
    private final Map<String, Http> http;
    private final Http defaultHttp;
    private final Collection<Phase> phases;
    private final Map<String, Object> tags;
    private final long statisticsCollectionPeriod;


    public Benchmark(String name, String originalSource, Host[] agents, int threads, Ergonomics ergonomics,
                     Map<String, Http> http, Collection<Phase> phases,
                     Map<String, Object> tags, long statisticsCollectionPeriod) {
        this.name = name;
        this.originalSource = originalSource;
        this.agents = agents;
        this.threads = threads;
        this.ergonomics = ergonomics;
        this.http = http;
        this.defaultHttp = http.values().stream().filter(Http::isDefault).findFirst().orElse(null);
        this.phases = phases;
        this.tags = tags;
        this.statisticsCollectionPeriod = statisticsCollectionPeriod;
    }

    public String name() {
        return name;
    }

    public Host[] agents() {
        return agents;
    }

    /**
     *  As the transformation from YAML is one-way (due to forks and iterations)
     *  here we store the original source (be it YAML or JSON)
     */
    public String source() {
        return originalSource;
    }

    public int threads() {
        return threads;
    }

    public Collection<Phase> phases() {
        return phases;
    }

    public Map<String, Object> tags() {
        return tags;
    }

    public Map<String, Http> http() {
        return http;
    }

    public Http defaultHttp() {
        return defaultHttp;
    }

    public long statisticsCollectionPeriod() {
        return statisticsCollectionPeriod;
    }

    @Override
    public String toString() {
        return "Benchmark{name='" + name + '\'' +
            ", originalSource='" + originalSource + '\'' +
            ", agents=" + Arrays.toString(agents) +
            ", threads=" + threads +
            ", http=" + http +
            ", phases=" + phases +
            ", tags=" + tags +
            ", statisticsCollectionPeriod=" + statisticsCollectionPeriod +
        '}';
    }

    public Stream<Step> steps() {
        return phases().stream()
             .flatMap(phase -> Stream.of(phase.scenario().sequences()))
             .flatMap(sequence -> Stream.of(sequence.steps()));
    }
}
