package io.hyperfoil.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.connection.SharedConnectionPool;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxTestContext;

public class ConnectionPoolRaceConditionTest extends VertxBaseTest {

   /** Holds the first server-side request so the test can delay and then release it. */
   private final CompletableFuture<HttpServerRequest> pendingRequest = new CompletableFuture<>();
   private HttpServer httpServer;

   @BeforeEach
   public void before(VertxTestContext ctx) {
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         // Park the first request; the test will unblock it after the race is set up.
         if (!pendingRequest.isDone()) {
            pendingRequest.complete(req);
         } else {
            req.response().setStatusCode(200).end("OK");
         }
      });
      httpServer.listen(0, "localhost").onComplete(ctx.succeedingThenComplete());
      cleanup.add(httpServer::close);
   }

   @Test
   public void testInFlightNotLeakedAfterSessionTerminatePulse(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();

      getClientPool(ctx).onComplete(result -> {
         if (result.failed())
            return;
         HttpClientPool client = result.result();

         SharedConnectionPool pool = (SharedConnectionPool) client.next();
         pool.executor().execute(() -> {
            Session session = SessionFactory.forTesting();
            HttpRunData.initForTesting(session);
            HttpRequest request = HttpRequestPool.get(session).acquire();
            request.method = HttpMethod.GET;
            request.path = "/";

            HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
                  .onCompletion(s -> {
                     pool.executor().execute(() -> ctx.verify(() -> {
                        assertThat(pool.availableCount())
                              .as("available deque should have exactly 1 entry; " +
                                    "a count of 2 means the !shouldPulse path created a duplicate")
                              .isEqualTo(1);

                        checkpoint.flag();
                     }));
                  }).build();

            request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
            pool.acquire(false, c -> {
               request.send(c, null, true, null);

               pendingRequest.thenAccept(req -> {
                  pool.executor().execute(() -> {
                     pool.onSessionTryTerminate();
                     pool.pulse();
                     req.response().setStatusCode(200).end("OK");
                  });
               });
            });
         });
      });
   }

   private Future<HttpClientPool> getClientPool(VertxTestContext ctx) {
      Http http = HttpBuilder.forTesting().protocol(Protocol.HTTP)
            .host("localhost").port(httpServer.actualPort())
            .sharedConnections(1)
            .build(true);
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
}
