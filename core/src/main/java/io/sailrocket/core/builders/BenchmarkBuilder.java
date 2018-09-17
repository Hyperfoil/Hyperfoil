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

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.Host;
import io.sailrocket.api.SLA;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

    private String name;
    private SimulationBuilder simulation;
    private Host[] hosts;
    private Collection<SLABuilder> slas = new ArrayList<>();

    private BenchmarkBuilder() {
    }

    public static BenchmarkBuilder builder() {
        return new BenchmarkBuilder();
    }

    public BenchmarkBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SimulationBuilder simulation() {
        return simulation = new SimulationBuilder(this);
    }

    public BenchmarkBuilder host(String host){
        return this;
    }

    public Benchmark build() {
        return new Benchmark(name, simulation.build(), hosts, slas.stream().map(SLABuilder::build).toArray(SLA[]::new));
    }

    void addSLA(SLABuilder sla) {
        slas.add(sla);
    }
}
