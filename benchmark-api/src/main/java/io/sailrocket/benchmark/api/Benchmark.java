package io.sailrocket.benchmark.api;

import java.util.Set;

public abstract class Benchmark {

    private String name;
    Set<Result> resultSet;

    public Benchmark(String name) {
        this.name = name;
    }

    Benchmark simulation(Simulation simulation){
        return this;
    }

    Benchmark scale(ScalingStrategy scalingStrategy){
        return this;
    }

    Benchmark sla(SLA sla){
        return this;
    }

    abstract void run();

}
