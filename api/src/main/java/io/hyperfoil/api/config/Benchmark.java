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
import java.util.stream.Stream;

/**
 * A benchmark is a collection of simulation, user,
 * SLA and scaling strategy (Ramp up, Steady State, Ramp Down, steady state variance)
 * that are to be run against the target environment.
 */
public class Benchmark implements Serializable {

    protected final String name;
    protected final String originalSource;
    protected final Simulation simulation;
    protected final Host[] agents;

    public Benchmark(String name, String originalSource, Simulation simulation, Host[] agents) {
        this.name = name;
        this.originalSource = originalSource;
        this.simulation = simulation;
        this.agents = agents;
    }

    public Simulation simulation() {
        return simulation;
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

    @Override
    public String toString() {
        return "Benchmark{" +
                       "name='" + name + '\'' +
                       ", originalSource='" + originalSource + '\'' +
                       ", simulation=" + simulation +
                       ", agents=" + Arrays.toString(agents) +
                       '}';
    }

    public Stream<Step> steps() {
        return simulation.phases().stream()
             .flatMap(phase -> Stream.of(phase.scenario().sequences()))
             .flatMap(sequence -> Stream.of(sequence.steps()));
    }
}
