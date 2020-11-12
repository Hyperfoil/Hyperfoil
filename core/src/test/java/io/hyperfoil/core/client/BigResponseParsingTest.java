package io.hyperfoil.core.client;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.Protocol;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.HttpResponseHandlersImpl;
import io.netty.buffer.ByteBuf;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class BigResponseParsingTest extends VertxBaseTest {
   private Vertx vertx = Vertx.vertx();
   private HttpServer httpServer;

   @Before
   public void before(TestContext ctx) {
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         AtomicInteger counter = new AtomicInteger(100000);
         req.response().putHeader("content-length", String.valueOf(counter.get()));
         sendChunk(req, counter);
      }).listen(0, "localhost", ctx.asyncAssertSuccess());
      cleanup.add(httpServer::close);
   }

   private void sendChunk(HttpServerRequest req, AtomicInteger counter) {
      req.response().write(Buffer.buffer(new byte[10000]), result -> {
         if (counter.addAndGet(-10000) == 0) {
            req.response().end();
         } else {
            sendChunk(req, counter);
         }
      });
   }

   private Future<HttpClientPool> getClientPool(TestContext ctx) {
      Http http = HttpBuilder.forTesting().protocol(Protocol.HTTP)
            .host("localhost").port(httpServer.actualPort())
            .allowHttp2(false).build(true);
      try {
         HttpClientPool client = HttpClientPoolImpl.forTesting(http, 1);

         Promise<HttpClientPool> promise = Promise.promise();
         client.start(result -> {
            if (result.failed()) {
               ctx.fail(result.cause());
               promise.fail(result.cause());
               return;
            }
            cleanup.add(client::shutdown);
            promise.complete(client);
         });
         return promise.future();
      } catch (SSLException e) {
         return Future.failedFuture(e);
      }
   }

   @Test
   public void test(TestContext ctx) {
      Async async = ctx.async();
      AtomicInteger responseSize = new AtomicInteger();
      getClientPool(ctx).onComplete(result -> {
         HttpClientPool client = result.result();
         Session session = SessionFactory.forTesting();
         HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
               .rawBytes(new RawBytesHandler() {
                  @Override
                  public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                  }

                  @Override
                  public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
                     responseSize.addAndGet(length);
                  }
               })
               .onCompletion(s -> {
                  ctx.assertTrue(responseSize.get() > 100000, String.valueOf(responseSize.get()));
                  async.countDown();
               }).build();
         HttpRequest newRequest = session.httpRequestPool().acquire();
         newRequest.method = HttpMethod.GET;
         newRequest.path = "/";
         SequenceInstance sequence = new SequenceInstance().reset(null, 0, new Step[0], null);
         newRequest.start(handlers, sequence, new Statistics(System.currentTimeMillis()));
         ctx.assertTrue(client.next().request(newRequest, null, true, null, false));
      });
   }
}
