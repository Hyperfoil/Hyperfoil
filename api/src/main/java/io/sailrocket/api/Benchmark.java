package io.sailrocket.api;

import java.util.Set;

public abstract class Benchmark {

    protected String name;
    protected Set<Result> resultSet;
    protected String[] hosts;
    protected int users;
    protected String endpoint;

    public Benchmark(String name) {
        this.name = name;
    }

    public Benchmark simulation(Simulation simulation) {
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

    public Benchmark endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }


    public abstract void run();
}
