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

package io.hyperfoil.benchmark.standalone;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.handlers.ResponseSizeRecorder;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@Category(io.hyperfoil.test.Benchmark.class)
@RunWith(VertxUnitRunner.class)
public class RequestResponseCounterTest {

   private AtomicLong counter;
   private Vertx vertx = Vertx.vertx();
   private HttpServer httpServer;

   @Before
   public void before(TestContext ctx) {
      counter = new AtomicLong();
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         counter.getAndIncrement();
         req.response().end("hello from server");
      }).listen(0, "localhost", ctx.asyncAssertSuccess());
   }

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }

   @Test
   public void testNumberOfRequestsAndResponsesMatch() {
      // @formatter:off
      BenchmarkBuilder builder =
            new BenchmarkBuilder(null, new LocalBenchmarkData())
                  .name("requestResponseCounter " + new SimpleDateFormat("YY/MM/dd HH:mm:ss").format(new Date()))
                  .http()
                     .host("localhost").port(httpServer.actualPort())
                     .sharedConnections(50)
                  .endHttp()
                  .threads(2);

      builder.addPhase("run").constantPerSec(500)
            .duration(5000)
            .maxSessions(500 * 15)
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .path("/")
                     .timeout("60s")
                     .handler()
                        .rawBytes(new ResponseSizeRecorder("bytes"))
                     .endHandler()
                  .endStep()
               .endSequence();
      // @formatter:on

      Benchmark benchmark = builder.build();

      AtomicLong actualNumberOfRequests = new AtomicLong(0);
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark,
            (phase, isPhaseComplete, stepId, metric, snapshot, countDown) -> actualNumberOfRequests.addAndGet(snapshot.histogram.getTotalCount()), null);
      runner.run();

      assertEquals(counter.get(), actualNumberOfRequests.get());
   }

}
