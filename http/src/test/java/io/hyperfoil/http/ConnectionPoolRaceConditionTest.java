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
import io.hyperfoil.http.api.HttpConnection;
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

   /**
    * Scenario: duplicate-available-entry bug.
    *
    * A connection is on the wire (real HTTP request sent) when onSessionTryTerminate fires.
    * The !shouldPulse drain must add the connection to pendingDrainConnections rather than
    * immediately calling release(), because releasePoolAndPulse() will call release() once the
    * response arrives. Before the fix, the drain called release() unconditionally, causing the
    * connection to appear twice in the available deque.
    */
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
                        assertThat(pool.inFlightCount())
                              .as("pool-level inFlight counter must be 0 after response completes")
                              .isEqualTo(0);

                        checkpoint.flag();
                     }));
                  }).build();

            request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
            pool.acquire(false, c -> {
               request.send(c, null, true, null);

               // Once the request is on the wire, simulate session termination, then let the
               // server respond. The response triggers releasePoolAndPulse() which must find
               // shouldPulse still false (deferred via pendingDrainConnections) and restore it.
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

   /**
    * Scenario: orphaned-acquisition drain path.
    *
    * A connection is acquired (onAcquire called, aboutToSend == 1) but the session is reset
    * before request() is ever called — so no HTTP bytes go on the wire. When onSessionTryTerminate
    * fires, the drain must detect this via pendingRequestCount() == 0 && inFlight() > 0, cancel
    * the orphaned slot with cancelAcquire(), and return the connection as available. Pool-level
    * inFlight and usedConnections counters must both reach 0.
    */
   @Test
   public void testOrphanedAcquisitionDrainedWithoutCounterCorruption(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();

      getClientPool(ctx).onComplete(result -> {
         if (result.failed())
            return;
         HttpClientPool client = result.result();

         SharedConnectionPool pool = (SharedConnectionPool) client.next();
         pool.executor().execute(() -> {
            // Acquire the connection but never call request.send() — this leaves aboutToSend == 1
            // with no bytes on the wire, exactly the orphaned-acquisition state.
            pool.acquire(false, (HttpConnection conn) -> {
               // Verify the connection is indeed in the orphaned state before the drain.
               assertThat(conn.pendingRequestCount())
                     .as("no HTTP request sent yet, pendingRequestCount must be 0")
                     .isEqualTo(0);
               assertThat(conn.inFlight())
                     .as("onAcquire incremented aboutToSend, so inFlight must be 1")
                     .isEqualTo(1);

               // Simulate session termination while the acquisition slot is still open.
               pool.onSessionTryTerminate();
               pool.pulse();

               ctx.verify(() -> {
                  assertThat(pool.availableCount())
                        .as("connection must be returned to the available deque after orphaned drain")
                        .isEqualTo(1);
                  assertThat(pool.inFlightCount())
                        .as("pool inFlight counter must be 0 — cancelAcquire undoes the acquireNow increment")
                        .isEqualTo(0);
                  assertThat(conn.inFlight())
                        .as("connection-level inFlight must be 0 after cancelAcquire")
                        .isEqualTo(0);
               });

               checkpoint.flag();
            });
         });
      });
   }

   /**
    * Scenario: deferred-drain lifecycle — shouldPulse stays false until the last in-flight
    * connection responds, then restores to true so waiting consumers can proceed.
    *
    * One connection sends a real request (parked at the server). While the request is on the
    * wire, onSessionTryTerminate fires: shouldPulse goes false and the connection is added to
    * pendingDrainConnections. A second acquire() call joins the waiting queue. Once the server
    * responds, release() removes the connection from pendingDrainConnections, restores
    * shouldPulse, and calls pulse() to serve the waiting consumer — without creating duplicate
    * entries in the available deque.
    */
   @Test
   public void testDeferredDrainRestoresShouldPulseAndServesWaitingConsumer(VertxTestContext ctx) {
      // Two checkpoints: one for the in-flight response, one for the waiting consumer.
      var inFlightCheckpoint = ctx.checkpoint();
      var waitingCheckpoint = ctx.checkpoint();

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
                     // The response has arrived and releasePoolAndPulse() has fired, which:
                     //   1. called release() → shouldPulse restored to true
                     //   2. called pulse()   → waiting consumer served, connection re-acquired
                     // By the time onCompletion runs the waiting consumer already holds the
                     // connection, so inFlight == 1 and available == 0. We only flag here to
                     // confirm the response path completed; counter correctness is covered by
                     // testInFlightNotLeakedAfterSessionTerminatePulse.
                     pool.executor().execute(() -> inFlightCheckpoint.flag());
                  }).build();

            request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
            pool.acquire(false, c -> {
               request.send(c, null, true, null);

               pendingRequest.thenAccept(req -> {
                  pool.executor().execute(() -> {
                     // Session terminates while the request is on the wire.
                     pool.onSessionTryTerminate();
                     pool.pulse();

                     // A second consumer joins the waiting queue while shouldPulse == false.
                     // It must be served once the in-flight response arrives and release()
                     // restores shouldPulse.
                     pool.acquire(false, waiting -> ctx.verify(() -> {
                        assertThat(waiting)
                              .as("waiting consumer must receive a connection after shouldPulse is restored")
                              .isNotNull();
                        // Return it so the pool stays consistent.
                        pool.release(waiting, true, false);
                        waitingCheckpoint.flag();
                     }));

                     // Unblock the server — triggers releasePoolAndPulse() which restores shouldPulse.
                     req.response().setStatusCode(200).end("OK");
                  });
               });
            });
         });
      });
   }

   @Test
   public void testAcceptNullRequestLeaksPoolInFlight(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();

      getClientPool(ctx).onComplete(result -> {
         if (result.failed())
            return;
         HttpClientPool client = result.result();

         SharedConnectionPool pool = (SharedConnectionPool) client.next();
         pool.executor().execute(() -> {
            Session session = SessionFactory.forTesting();
            HttpRunData.initForTesting(session);

            // Acquire the only connection so the pool is exhausted
            pool.acquire(false, (HttpConnection firstConn) -> {

               // Mimic real-world initialization where the connection has been attached to a pool
               firstConn.attach(pool);

               // Put a consumer in the waiting queue that mimics
               // HttpRequestContext.accept() when request==null
               pool.acquire(false, (HttpConnection conn) -> {
                  if (conn != null && conn.pool() != null) {
                     conn.pool().cancelAcquire(conn);
                  }

                  ctx.verify(() -> {
                     assertThat(pool.inFlightCount())
                           .as("pool inFlight should be 0 — cancelAcquire undoes conn-level but not pool-level")
                           .isEqualTo(0);
                  });
                  checkpoint.flag();
               });

               // Release firstConn to trigger pulse() which pops the waiting consumer
               firstConn.cancelAcquire();
               pool.release(firstConn, true, true);
               pool.pulse();
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
