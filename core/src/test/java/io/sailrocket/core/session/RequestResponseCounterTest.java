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

package io.sailrocket.core.session;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.builders.SimulationBuilder;
import io.sailrocket.core.extractors.ByteBufSizeRecorder;
import io.sailrocket.core.impl.LocalSimulationRunner;
import io.sailrocket.core.impl.statistics.StatisticsCollector;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@RunWith(VertxUnitRunner.class)
public class RequestResponseCounterTest {

    private AtomicLong counter;
    private Vertx vertx = Vertx.vertx();

    @Before
    public void before(TestContext ctx) {
        counter = new AtomicLong();
        vertx.createHttpServer().requestHandler(req -> {
            counter.getAndIncrement();
            req.response().end("hello from server");
        }).listen(8088, "localhost", ctx.asyncAssertSuccess());
    }

    @After
    public void after(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void testNumberOfRequestsAndResponsesMatch() {

        SimulationBuilder simulationBuilder =
                new BenchmarkBuilder(null)
                        .name("requestResponseCounter " + new SimpleDateFormat("YY/MM/dd HH:mm:ss").format(new Date()))
                        .simulation()
                        .http()
                        .baseUrl("http://localhost:8088/")
                        .sharedConnections(50)
                        .endHttp()
                        .threads(2);

        simulationBuilder.addPhase("run").constantPerSec(500)
                .duration(20)
                .maxSessionsEstimate(500 * 15)
                .scenario()
                .initialSequence("request")
                .step().httpRequest(HttpMethod.GET)
                .path("/")
                .timeout("60s")
                .handler()
                .rawBytesHandler(new ByteBufSizeRecorder("bytes"))
                .endHandler()
                .endStep()
                .step().awaitAllResponses()
                .endSequence()
                .endScenario();

        Benchmark benchmark = simulationBuilder.endSimulation().build();

        LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
        System.out.println("starting the run");
        runner.run();
        System.out.println("finished running");
        StatisticsCollector collector = new StatisticsCollector(benchmark.simulation());
        runner.visitSessions(collector);

        AtomicLong actualNumberOfRequests = new AtomicLong();
        collector.visitStatistics((phase, sequence, snapshot) -> {
            actualNumberOfRequests.set(snapshot.histogram.getTotalCount());
        });

        assertEquals(counter.get(), actualNumberOfRequests.get());

    }

}
