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

package io.hyperfoil.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class HttpClientPoolHandlerTest {

   protected volatile int count;
   private Vertx vertx = Vertx.vertx();
   private HttpServer httpServer;

   @Before
   public void before(TestContext ctx) {
      count = 0;
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         count++;
         req.response().putHeader("foo", "bar").end("hello from server");
      }).listen(0, "localhost", ctx.asyncAssertSuccess());
   }

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }

   @Test
   public void simpleHeaderRequest(TestContext ctx) throws Exception {
      Http http = HttpBuilder.forTesting().host("localhost").port(httpServer.actualPort()).build(true);
      HttpClientPool client = HttpClientPoolImpl.forTesting(http, 1);

      CountDownLatch startLatch = new CountDownLatch(1);
      client.start(result -> {
         if (result.failed()) {
            ctx.fail(result.cause());
         } else {
            startLatch.countDown();
         }
      });
      assertThat(startLatch.await(10, TimeUnit.SECONDS)).isTrue();


      CountDownLatch latch = new CountDownLatch(4);
      HttpConnectionPool pool = client.next();
      pool.executor().execute(() -> {
         Session session = SessionFactory.forTesting();
         HttpRunData.initForTesting(session);
         session.declareResources().build();
         HttpRequest request = HttpRequestPool.get(session).acquire();
         HttpResponseHandlersImpl handlers = HttpResponseHandlersImpl.Builder.forTesting()
               .status((r, code) -> {
                  assertThat(code).isEqualTo(200);
                  latch.countDown();
               })
               .header((req, header, value) -> {
                  if ("foo".contentEquals(header)) {
                     assertThat("bar").asString().isEqualTo("bar");
                     latch.countDown();
                  }
               })
               .body(f -> (s, input, offset, length, isLastPart) -> {
                  byte[] bytes = new byte[length];
                  input.getBytes(offset, bytes);
                  assertThat(new String(bytes)).isEqualTo("hello from server");
                  latch.countDown();
               })
               .onCompletion(s -> latch.countDown())
               .build();
         request.method = HttpMethod.GET;
         request.path = "/";
         request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
         pool.acquire(false, c -> request.send(c, null, true, null));
      });

      assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(count).isEqualTo(1);

      client.shutdown();
   }
}
