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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class HttpClientPoolHandlerTest {

   protected AtomicInteger count;
   private HttpServer httpServer;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      count = new AtomicInteger(0);
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         count.getAndIncrement();
         req.response().putHeader("foo", "bar").end("hello from server");
      }).listen(0, "localhost", ctx.succeedingThenComplete());
   }

   @Test
   public void simpleHeaderRequest(VertxTestContext ctx) throws Exception {
      Http http = HttpBuilder.forTesting().host("localhost").port(httpServer.actualPort()).build(true);
      HttpClientPool client = HttpClientPoolImpl.forTesting(http, 1);

      var checkpoint = ctx.checkpoint(5);
      CountDownLatch startLatch = new CountDownLatch(1);
      client.start(result -> {
         if (result.failed()) {
            ctx.failNow(result.cause());
         } else {
            startLatch.countDown();
            checkpoint.flag();
         }
      });
      assertThat(startLatch.await(10, TimeUnit.SECONDS)).isTrue();

      CountDownLatch latch = new CountDownLatch(4);
      HttpConnectionPool pool = client.next();
      pool.executor().execute(() -> {
         Session session = SessionFactory.forTesting();
         HttpRunData.initForTesting(session);
         HttpRequest request = HttpRequestPool.get(session).acquire();
         HttpResponseHandlersImpl handlers = HttpResponseHandlersImpl.Builder.forTesting()
               .status((r, code) -> {
                  ctx.verify(() -> {
                     assertThat(code).isEqualTo(200);
                     checkpoint.flag();
                  });
                  latch.countDown();
               })
               .header((req, header, value) -> {
                  if ("foo".contentEquals(header)) {
                     ctx.verify(() -> {
                        assertThat(value.toString()).asString().isEqualTo("bar");
                        checkpoint.flag();
                     });
                     latch.countDown();
                  }
               })
               .body(f -> (s, input, offset, length, isLastPart) -> {
                  byte[] bytes = new byte[length];
                  input.getBytes(offset, bytes);
                  ctx.verify(() -> {
                     assertThat(new String(bytes)).isEqualTo("hello from server");
                     checkpoint.flag();
                  });
                  latch.countDown();
               })
               .onCompletion(s -> {
                  latch.countDown();
                  checkpoint.flag();
               })
               .build();
         request.method = HttpMethod.GET;
         request.path = "/";
         request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
         pool.acquire(false, c -> request.send(c, null, true, null));
      });

      assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(count.get()).isEqualTo(1);

      client.shutdown();
   }
}
