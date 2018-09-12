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
package io.sailrocket.api;


import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * A benchmark is a collection of simulation, user,
 * SLA and scaling strategy (Ramp up, Steady State, Ramp Down, steady state variance)
 * that are to be run against the target environment.
 */
public abstract class Benchmark implements Serializable {

    protected String name;
    protected Set<Result> resultSet;
    protected String[] hosts;
    protected SLA[] slas;
//    protected Phase[] phases;
    protected int users;
    protected Simulation simulation;

    public Benchmark() {}

    public Benchmark(String name) {
        this.name = name;
    }

    public Benchmark simulation(Simulation simulation) {
        this.simulation = simulation;
        return this;
    }

    public Benchmark scale(ScalingStrategy scalingStrategy) {
        return this;
    }

    public Benchmark sla(SLA sla) {
        return this;
    }

    public Benchmark agents(String... hosts) {
        this.hosts = hosts;
        return this;
    }

    public Benchmark users(int users) {
        this.users = users;
        return this;
    }

    public abstract Map<String, Report> run() throws BenchmarkDefinitionException;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
