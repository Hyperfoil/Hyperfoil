package io.sailrocket.core;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.HttpClientRunner;
import io.vertx.core.http.HttpVersion;

import java.util.Arrays;

public class BenchmarkImpl extends Benchmark {
    public BenchmarkImpl(String name) {
        super(name);
    }

    @Override
    public void run() {

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
