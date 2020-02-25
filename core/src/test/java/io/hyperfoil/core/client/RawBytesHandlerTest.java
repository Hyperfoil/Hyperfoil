package io.hyperfoil.core.client;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.Protocol;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.HttpResponseHandlersImpl;
import io.hyperfoil.core.test.TestUtil;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class RawBytesHandlerTest extends VertxBaseTest {
   @Test
   public void test(TestContext ctx) {
      Async async = ctx.async(1000);
      HttpServer httpServer = vertx.createHttpServer();
      httpServer.requestHandler(this::handler).listen(0, "localhost", event -> {
         if (event.failed()) {
            ctx.fail(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               HttpClientPool client = new HttpClientPoolImpl(1, HttpBuilder.forTesting()
                     .protocol(Protocol.HTTP).host("localhost").port(server.actualPort()).allowHttp2(false).build(true));
               client.start(result -> {
                  if (result.failed()) {
                     ctx.fail(result.cause());
                     return;
                  }
                  cleanup.add(client::shutdown);
                  Session session = SessionFactory.forTesting();
                  AtomicReference<HttpResponseHandlers> handlersRef = new AtomicReference<>();
                  handlersRef.set(HttpResponseHandlersImpl.Builder.forTesting()
                        .rawBytes((req, buf, offset, length, isLastPart) -> {
                        })
                        .onCompletion(s -> {
                           async.countDown();
                           if (!async.isCompleted()) {
                              HttpConnectionPool pool = client.next();
                              pool.executor().schedule(() -> {
                                 doRequest(ctx, session, handlersRef, pool);
                              }, 1, TimeUnit.NANOSECONDS);
                           }
                        }).build());
                  doRequest(ctx, session, handlersRef, client.next());
               });
            } catch (Exception e) {
               ctx.fail(e);
            }
         }
      });
   }

   private void doRequest(TestContext ctx, Session session, AtomicReference<HttpResponseHandlers> handlersRef, HttpConnectionPool pool) {
      HttpRequest newRequest = session.httpRequestPool().acquire();
      newRequest.method = HttpMethod.GET;
      newRequest.path = "/ping";
      newRequest.cacheControl.noCache = true;
      SequenceInstance sequence = new SequenceInstance();
      sequence.reset("foo", 0, 0, new Step[0]);
      newRequest.start(handlersRef.get(), sequence, new Statistics(System.currentTimeMillis()));
      ctx.assertTrue(pool.request(newRequest, null, true, null, false));
   }

   private void handler(HttpServerRequest request) {
      ThreadLocalRandom rand = ThreadLocalRandom.current();
      int headers = rand.nextInt(10);
      for (int i = 0; i < headers; ++i) {
         request.response().putHeader("x-foobar-" + i, TestUtil.randomString(rand, 100));
      }
      request.response().setChunked(true);
      request.response().end(TestUtil.randomString(rand, 2000));
   }

}
