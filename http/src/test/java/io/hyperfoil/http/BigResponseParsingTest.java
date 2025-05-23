package io.hyperfoil.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.session.SessionFactory;
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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class BigResponseParsingTest extends VertxBaseTest {
   private HttpServer httpServer;

   @BeforeEach
   public void before(VertxTestContext ctx) {
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         AtomicInteger counter = new AtomicInteger(100000);
         req.response().putHeader("content-length", String.valueOf(counter.get()));
         sendChunk(req, counter);
      }).listen(0, "localhost", ctx.succeedingThenComplete());
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

   private Future<HttpClientPool> getClientPool(VertxTestContext ctx) {
      Http http = HttpBuilder.forTesting().protocol(Protocol.HTTP)
            .host("localhost").port(httpServer.actualPort())
            .allowHttp2(false).build(true);
      try {
         HttpClientPool client = HttpClientPoolImpl.forTesting(http, 1);

         Promise<HttpClientPool> promise = Promise.promise();
         client.start(result -> {
            if (result.failed()) {
               ctx.failNow(result.cause());
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
   public void test(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();
      AtomicInteger responseSize = new AtomicInteger();
      getClientPool(ctx).onComplete(result -> {
         HttpClientPool client = result.result();
         Session session = SessionFactory.forTesting();
         HttpRunData.initForTesting(session);
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
                  // assertTrue(responseSize.get() > 100000, String.valueOf(responseSize.get()));
                  assertTrue(responseSize.get() > 100000, String.valueOf(responseSize.get()));
                  checkpoint.flag();
               }).build();
         HttpRequest newRequest = HttpRequestPool.get(session).acquire();
         newRequest.method = HttpMethod.GET;
         newRequest.path = "/";
         SequenceInstance sequence = new SequenceInstance().reset(null, 0, new Step[0], null);

         HttpConnectionPool pool = client.next();
         newRequest.start(pool, handlers, sequence, new Statistics(System.currentTimeMillis()));
         pool.acquire(false, c -> newRequest.send(c, null, true, null));
      });
   }
}
