package io.hyperfoil.http;

import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
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
import io.vertx.junit5.VertxTestContext;

/**
 * This test extends {@code VertxBaseTest} directly rather than {@code BaseClientTest} because it tests low-level
 * connection pool internals during session termination, not HTTP request/response flows.
 */
public class ConnectionPoolRaceConditionTest extends VertxBaseTest {

   private HttpServer httpServer;

   @BeforeEach
   public void before(VertxTestContext ctx) {
      httpServer = vertx.createHttpServer().requestHandler(req -> {
         req.response().setStatusCode(200);
         req.response().end("OK");
      });
      httpServer.listen(0, "localhost").onComplete(ctx.succeedingThenComplete());
      cleanup.add(httpServer::close);
   }

   /**
    * Test for race condition in connection pool during session termination.
    * <p>
    * The race condition occurs when:
    * 1. A connection is acquired from the pool (inFlight is incremented)
    * 2. Session termination is triggered, which calls onSessionTryTerminate()
    * 3. onSessionTryTerminate() sets shouldPulse = false
    * 4. When pulse() is called, it releases all connections with release(conn, true, false)
    * - This adds connections to available but does NOT decrement inFlight (afterRequest=false)
    * 5. If a connection was acquired for a request that's still in-flight, inFlight remains inflated
    * 6. When the response completes, it calls release(conn, true, true) which adds the connection
    * to available a second time (ArrayDeque allows duplicates)
    */
   @Test
   public void testConnectionPoolRaceCondition(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();
      AtomicInteger responseCount = new AtomicInteger();
      int maxRetries = 5;

      getClientPool(ctx).onComplete(result -> {
         HttpClientPool client = result.result();
         Session session = SessionFactory.forTesting();
         HttpRunData.initForTesting(session);
         HttpRequest request = HttpRequestPool.get(session).acquire();
         request.method = HttpMethod.GET;
         request.path = "/";

         HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
               .onCompletion(s -> {
                  responseCount.addAndGet(1);
                  if (responseCount.get() >= 1) {
                     // Verify the pool state after race condition
                     // Access the shared connection pool and check stats
                     SharedConnectionPool sharedPool = (SharedConnectionPool) client.next();

                     // Check that connections are available (they should be released back to pool)
                     java.util.Collection<HttpConnection> connections = sharedPool.connections();
                     int availableCount = (int) connections.stream().filter(c -> !c.isClosed()).count();
                     if (availableCount < 1) {
                        ctx.failNow("No connections available after race condition - available count: " + availableCount);
                        return;
                     }

                     // Check that inFlight count is correct (should be 0 after response completes)
                     int inFlightCount = sharedPool.inFlightCount();
                     if (inFlightCount != 0) {
                        ctx.failNow("inFlight count is inflated: " + inFlightCount + " - race condition detected!");
                        return;
                     }

                     checkpoint.flag();
                  }
               }).build();
         HttpConnectionPool pool = client.next();
         request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
         // pool.acquire requires to be running within the event loop
         // the acquire is called from onComplete callback that can run on different thread
         // it can fail on CI sometimes. then, we add pool.executor to ensure that it will be running on the event loop.
         pool.executor().execute(() -> pool.acquire(false, c -> request.send(c, null, true, null)));

         // Trigger session termination to trigger the race condition
         // This sets shouldPulse = false, and the next pulse() call will release all connections
         // with release(conn, true, false) which doesn't decrement inFlight
         session.tryTerminate();
      });

      // Retry the test multiple times to increase the chance of triggering the race condition
      for (int i = 0; i < maxRetries; i++) {
         if (responseCount.get() > 0) {
            break;
         }
      }
   }

   private Future<HttpClientPool> getClientPool(VertxTestContext ctx) {
      Http http = HttpBuilder.forTesting().protocol(Protocol.HTTP)
            .host("localhost").port(httpServer.actualPort())
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
