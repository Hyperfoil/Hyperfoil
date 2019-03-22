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
package io.hyperfoil.api.config;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

    private final String originalSource;
    private String name;
    private final SimulationBuilder simulation = new SimulationBuilder(this);
    private Collection<Host> agents = new ArrayList<>();

    public BenchmarkBuilder(String originalSource) {
        this.originalSource = originalSource;
    }

    public static BenchmarkBuilder builder() {
        return new BenchmarkBuilder(null);
    }

    public BenchmarkBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SimulationBuilder simulation() {
        return simulation;
    }

    public BenchmarkBuilder addAgent(String name, String hostname, String username, int port){
        agents.add(new Host(name, hostname, username, port));
        return this;
    }

    public Benchmark build() {
        FutureSupplier<Benchmark> bs = new FutureSupplier<>();
        simulation.prepareBuild();
        Benchmark benchmark = new Benchmark(name, originalSource, simulation.build(bs), agents.toArray(new Host[0]));
        bs.set(benchmark);
        return benchmark;
    }

    public BenchmarkBuilder addAgent(String name, String usernameHostPort) {
        agents.add(Host.parse(name, usernameHostPort));
        return this;
    }

    int numAgents() {
        return agents.size();
    }
}
