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

import io.sailrocket.api.Scenario;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.SimulationImpl;
import io.sailrocket.spi.HttpBase;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SimulationBuilder {

    private HttpBase http;
    private long duration;
    private int connections = 1;
    private int concurrency = 1;
    private int threads = 1;
    private long rampDown;
    private long rampUp;
    private String payload = "";
    //for now just create one scenario
    //TODO:: this needs to be a collection of scenarios, and we need to add the simulation when it is built
    private Scenario scenario;
    private int rate = 100;
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

    public SimulationBuilder duration(String duration) {
        return apply(clone -> clone.duration = parseDuration(duration));
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

    public SimulationBuilder rampUp(String duration) {
        return apply(clone -> clone.rampUp = parseDuration(duration));
    }

    public SimulationBuilder rampDown(String duration) {
        return apply(clone -> clone.rampDown = parseDuration(duration));
    }

    public SimulationBuilder payload(String payload) {
        return apply(clone -> clone.payload = payload);
    }

    public SimulationBuilder rate(int rate) {
        return apply(clone -> clone.rate = rate);
    }


    public SimulationBuilder scenario(Scenario scenario) {
        return apply(clone -> clone.scenario = scenario);
    }

    public SimulationBuilder scenario(ScenarioBuilder scenarioBuilder) {
        return scenario(scenarioBuilder.build());
    }

    private static long parseDuration(String s) {
        TimeUnit unit;
        String prefix;
        switch (s.charAt(s.length() - 1)) {
            case 's':
                unit = TimeUnit.SECONDS;
                prefix = s.substring(0, s.length() - 1);
                break;
            case 'm':
                unit = TimeUnit.MINUTES;
                prefix = s.substring(0, s.length() - 1);
                break;
            case 'h':
                unit = TimeUnit.HOURS;
                prefix = s.substring(0, s.length() - 1);
                break;
            default:
                unit = TimeUnit.SECONDS;
                prefix = s;
                break;
        }
        return unit.toNanos(Long.parseLong(prefix));
    }

    public SimulationImpl build() {
        return (SimulationImpl) new SimulationImpl(threads, rate, duration, rampUp, buildClientPoolFactory(), buildTags()).scenario(this.scenario);
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
        tags.put("payloadSize", payload.length());
        tags.put("url", http.baseUrl().toString());
        tags.put("rate", rate);
        tags.put("protocol", http.baseUrl().protocol().version().toString());
        tags.put("maxQueue", concurrency);
        tags.put("connections", connections);
        tags.put("rate", rate);
        tags.put("threads", threads);

        return tags;
    }


}
