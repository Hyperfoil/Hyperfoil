package io.sailrocket.benchmark.api;

import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.HttpClientRunner;
import io.vertx.core.http.HttpVersion;

import java.util.Arrays;
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


    public void run(){

        HttpClientRunner httpClient = new HttpClientRunner();
        httpClient.provider = HttpClientProvider.vertx;
        httpClient.connections = this.users;
        httpClient.threads = 4;
        httpClient.uriParam = Arrays.asList(this.endpoint);
        httpClient.durationParam = "5s";
        httpClient.concurrency = 1;
        httpClient.rates = Arrays.asList(1000);
        httpClient.protocol = HttpVersion.HTTP_1_1;

        try {
            httpClient.run();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
