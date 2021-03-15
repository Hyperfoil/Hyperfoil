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

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@Category(io.hyperfoil.test.Benchmark.class)
@RunWith(VertxUnitRunner.class)
public class RequestResponseCounterTest extends BaseBenchmarkTest {
   private AtomicLong counter;

   @Before
   public void before(TestContext ctx) {
      super.before(ctx);
      counter = new AtomicLong();
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> {
         counter.getAndIncrement();
         req.response().end("hello from server");
      };
   }

   @Test
   public void testNumberOfRequestsAndResponsesMatch() {
      // @formatter:off
      BenchmarkBuilder builder =
            new BenchmarkBuilder(null, BenchmarkData.EMPTY)
                  .name("requestResponseCounter " + new SimpleDateFormat("YY/MM/dd HH:mm:ss").format(new Date()))
                  .addPlugin(HttpPluginBuilder::new).http()
                     .host("localhost").port(httpServer.actualPort())
                     .sharedConnections(50)
                  .endHttp().endPlugin()
                  .threads(2);

      builder.addPhase("run").constantRate(500)
            .duration(5000)
            .maxSessions(500 * 15)
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .path("/")
                     .timeout("60s")
                     .handler()
                        .rawBytes(new TransferSizeRecorder("sent", "received"))
                     .endHandler()
                  .endStep()
               .endSequence();
      // @formatter:on

      Benchmark benchmark = builder.build();

      AtomicLong actualNumberOfRequests = new AtomicLong(0);
      StatisticsCollector.StatisticsConsumer statisticsConsumer =
            (phase, stepId, metric, snapshot, countDown) -> actualNumberOfRequests.addAndGet(snapshot.histogram.getTotalCount());
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, statisticsConsumer, null, null);
      runner.run();

      assertEquals(counter.get(), actualNumberOfRequests.get());
   }

}
