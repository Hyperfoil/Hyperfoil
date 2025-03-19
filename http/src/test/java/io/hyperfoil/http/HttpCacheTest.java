package io.hyperfoil.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestClock;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class HttpCacheTest extends VertxBaseTest {
   private static final Logger log = LogManager.getLogger(HttpCacheTest.class);
   private static final TestClock CLOCK = new TestClock();
   private static final Consumer<HttpRequest> GET_TEST = request -> {
      request.method = HttpMethod.GET;
      request.path = "/test";
   };
   private static final Consumer<HttpRequest> POST_TEST = request -> {
      request.method = HttpMethod.POST;
      request.path = "/test";
   };

   @Test
   public void testSimpleRequest(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();
      Context context = new Context();

      // First request
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 1);
         assertCacheHits(ctx, req, 0);
      });

      // Second request, cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 1);
         assertCacheHits(ctx, req, 1);
      });

      // POST invalidates the cache
      context.requests.add(() -> doRequest(context, POST_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 2);
         assertEquals(HttpCache.get(context.session).size(), 0);
         assertCacheHits(ctx, req, 0);
      });

      // 4th request is not cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 3);
         assertCacheHits(ctx, req, 0);
      });

      // 5th request, cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 3);
         assertEquals(HttpCache.get(context.session).size(), 1);
         assertTrue(context.serverQueue.isEmpty());
         assertCacheHits(ctx, req, 1);
         checkpoint.flag();
      });

      test(ctx, context, true);
   }

   @Test
   public void testExpiration(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();
      Context context = new Context();

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response()
            .putHeader(HttpHeaderNames.EXPIRES, HttpUtil.formatDate(CLOCK.instant().plusSeconds(5).toEpochMilli())).end());
      context.handlers.add(req -> {
         assertEquals(1, context.serverRequests.get());
         assertCacheHits(ctx, req, 0);
      });

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(req -> {
         assertEquals(1, context.serverRequests.get());
         assertCacheHits(ctx, req, 1);
         CLOCK.advance(6000);
      });

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(2, context.serverRequests.get());
         assertCacheHits(ctx, req, 0);
      });

      context.requests.add(
            () -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.CACHE_CONTROL, "max-stale=10")));
      context.handlers.add(req -> {
         assertEquals(2, context.serverRequests.get());
         assertEquals(1, HttpCache.get(context.session).size());
         assertTrue(context.serverQueue.isEmpty());
         assertCacheHits(ctx, req, 1);
         checkpoint.flag();
      });

      test(ctx, context, true);
   }

   @Test
   public void testEtag(VertxTestContext ctx) {
      var checkpoint = ctx.checkpoint();
      Context context = new Context();

      // First request
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().putHeader(HttpHeaderNames.ETAG, "\"foo\"").end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 1);
         assertCacheHits(ctx, req, 0);
      });

      // We have 'bar' and 'foo', should get cached as 'foo' is in the cache
      context.requests.add(() -> doRequest(context, GET_TEST,
            (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\", \"foo\"")));
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 1);
         assertCacheHits(ctx, req, 1);
      });

      // We have 'bar' but this is not in the cache yet -> not cached
      context.requests
            .add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\"")));
      context.serverQueue.add(req -> req.response().putHeader(HttpHeaderNames.ETAG, "\"bar\"").end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 2);
         assertEquals(HttpCache.get(context.session).size(), 2);
         assertCacheHits(ctx, req, 0);
      });

      // foo still cached
      context.requests
            .add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"foo\"")));
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 2);
         assertCacheHits(ctx, req, 1);
      });

      // bar still cached
      context.requests
            .add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\"")));
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 2);
         assertCacheHits(ctx, req, 1);
         checkpoint.flag();
      });

      test(ctx, context, true);
   }

   @Test
   public void testWithoutCache(VertxTestContext ctx) {
      // applies the same test logic of #testSimpleRequest but with cache disabled
      var checkpoint = ctx.checkpoint();
      Context context = new Context();

      // First request
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 1);
         assertCacheHits(ctx, req, 0);
      });

      // Second request, cached but cache is disabled, hence still 0 hits
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 2);
         assertCacheHits(ctx, req, 0);
      });

      // POST it would have invalidated the cache, but no cache, hence still 0
      context.requests.add(() -> doRequest(context, POST_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 3);
         assertNull(HttpCache.get(context.session));
         assertCacheHits(ctx, req, 0);
      });

      // 4th request is not cached as the cache is disabled
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 4);
         assertCacheHits(ctx, req, 0);
      });

      // 5th request, it would have been cached if cache was  enabled, hence still 0 hits
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(req -> {
         assertEquals(context.serverRequests.get(), 5);
         assertNull(HttpCache.get(context.session));
         assertTrue(context.serverQueue.isEmpty());
         assertCacheHits(ctx, req, 0);
         checkpoint.flag();
      });

      test(ctx, context, false);
   }

   private void assertCacheHits(VertxTestContext ctx, HttpRequest req, int hits) {
      assertStats(req, snapshot -> assertEquals(HttpStats.get(snapshot).cacheHits, hits));
   }

   private void assertStats(HttpRequest request, Consumer<StatisticsSnapshot> consumer) {
      Statistics statistics = request.statistics();
      statistics.end(System.currentTimeMillis());
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      statistics.visitSnapshots(snapshot::add);
      consumer.accept(snapshot);
   }

   private void test(VertxTestContext ctx, Context context, boolean cacheEnabled) {
      assert !context.requests.isEmpty();
      vertx.createHttpServer().requestHandler(req -> {
         Consumer<HttpServerRequest> handler = context.serverQueue.poll();
         if (handler == null) {
            ctx.failNow("No handler for request.");
         }
         context.serverRequests.incrementAndGet();
         assert handler != null;
         handler.accept(req);
         if (!req.response().ended()) {
            ctx.failNow(("Response not sent"));
         }
      }).listen(0, "localhost", event -> {
         if (event.failed()) {
            ctx.failNow(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               HttpBuilder builder = HttpBuilder.forTesting().host("localhost").port(server.actualPort());
               HttpClientPool client = HttpClientPoolImpl.forTesting(builder.build(true), 1);
               client.start(result -> {
                  if (result.failed()) {
                     ctx.failNow(result.cause());
                     return;
                  }
                  cleanup.add(client::shutdown);
                  context.session = SessionFactory.forTesting();
                  HttpRunData.initForTesting(context.session, CLOCK, cacheEnabled);
                  context.pool = client;
                  Objects.requireNonNull(context.requests.poll()).run();
               });
            } catch (Exception e) {
               ctx.failNow(e);
            }
         }
      });
   }

   private void doRequest(Context context, Consumer<HttpRequest> configurator,
         BiConsumer<Session, HttpRequestWriter> headerAppender) {
      HttpRequest request = HttpRequestPool.get(context.session).acquire();
      HttpResponseHandlersImpl handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .onCompletion(s -> {
               Consumer<HttpRequest> handler = context.handlers.poll();
               if (handler != null) {
                  handler.accept(request);
                  Runnable command = context.requests.poll();
                  if (command == null) {
                     return;
                  }
                  command.run();
               }
            })
            .build();
      configurator.accept(request);
      log.trace("Sending {} request to {}", request.method, request.path);
      HttpConnectionPool pool = context.pool.next();
      request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      @SuppressWarnings("unchecked")
      BiConsumer<Session, HttpRequestWriter>[] headerAppenders = headerAppender == null ? null
            : new BiConsumer[] { headerAppender };
      pool.acquire(false, connection -> request.send(connection, headerAppenders, true, null));
   }

   private static class Context {
      Session session;
      HttpClientPool pool;
      Queue<Runnable> requests = new LinkedList<>();
      Queue<Consumer<HttpRequest>> handlers = new LinkedList<>();
      AtomicInteger serverRequests = new AtomicInteger();
      Queue<Consumer<HttpServerRequest>> serverQueue = new LinkedList<>();
   }
}
