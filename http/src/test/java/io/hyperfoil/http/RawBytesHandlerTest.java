package io.hyperfoil.http;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.netty.buffer.ByteBuf;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxTestContext;

public class RawBytesHandlerTest extends VertxBaseTest {
   private AtomicInteger numberOfPasses;

   @BeforeEach
   public void init() {
      numberOfPasses = new AtomicInteger(1000);
   }

   @Test
   public void test(Vertx vertx, VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint(numberOfPasses.get());
      HttpServer httpServer = vertx.createHttpServer();
      httpServer.requestHandler(this::handler).listen(0, "localhost", event -> {
         if (event.failed()) {
            ctx.failNow(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               Http http = HttpBuilder.forTesting().protocol(Protocol.HTTP)
                     .host("localhost").port(server.actualPort())
                     .allowHttp2(false).build(true);
               HttpClientPool client = HttpClientPoolImpl.forTesting(http, 1);
               client.start(result -> {
                  if (result.failed()) {
                     ctx.failNow(result.cause());
                     return;
                  }
                  cleanup.add(client::shutdown);
                  Session session = SessionFactory.forTesting();
                  HttpRunData.initForTesting(session);
                  AtomicReference<HttpResponseHandlers> handlersRef = new AtomicReference<>();
                  handlersRef.set(HttpResponseHandlersImpl.Builder.forTesting()
                        .rawBytes(new RawBytesHandler() {
                           @Override
                           public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                           }

                           @Override
                           public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
                           }
                        })
                        .onCompletion(s -> {
                           checkpoint.flag();
                           if (numberOfPasses.decrementAndGet() > 0) {
                              // we did not reach the required/expected num of passes
                              HttpConnectionPool pool = client.next();
                              pool.executor().schedule(() -> {
                                 doRequest(session, handlersRef, pool);
                              }, 1, TimeUnit.NANOSECONDS);
                           }
                        }).build());
                  doRequest(session, handlersRef, client.next());
               });
            } catch (Exception e) {
               ctx.failNow(e);
            }
         }
      });
   }

   private void doRequest(Session session, AtomicReference<HttpResponseHandlers> handlersRef,
         HttpConnectionPool pool) {
      HttpRequest newRequest = HttpRequestPool.get(session).acquire();
      newRequest.method = HttpMethod.GET;
      newRequest.path = "/ping";
      newRequest.cacheControl.noCache = true;
      SequenceInstance sequence = new SequenceInstance();
      sequence.reset(null, 0, new Step[0], null);
      newRequest.start(pool, handlersRef.get(), sequence, new Statistics(System.currentTimeMillis()));
      pool.acquire(false, c -> newRequest.send(c, null, true, null));
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
