package io.hyperfoil.core.http;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.HttpResponseHandlersImpl;
import io.hyperfoil.core.test.TestClock;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HttpCacheTest extends VertxBaseTest {
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
   public void testSimpleRequest(TestContext ctx) {
      Async async = ctx.async();
      Context context = new Context();

      // First request
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 1));

      // Second request, cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 1));

      // POST invalidates the cache
      context.requests.add(() -> doRequest(context, POST_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 2);
         ctx.assertEquals(context.session.httpCache().size(), 0);
      });

      // 4th request is not cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 3));

      // 5th request, cached
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 3);
         ctx.assertEquals(context.session.httpCache().size(), 1);
         ctx.assertTrue(context.serverQueue.isEmpty());
         async.countDown();
      });

      test(ctx, context);
   }

   @Test
   public void testExpiration(TestContext ctx) {
      Async async = ctx.async();
      Context context = new Context();

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response()
            .putHeader(HttpHeaderNames.EXPIRES, HttpUtil.formatDate(CLOCK.instant().plusSeconds(5).toEpochMilli())).end());
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 1));

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 1);
         CLOCK.advance(6000);
      });

      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().end());
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 2));

      context.requests.add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.CACHE_CONTROL, "max-stale=10")));
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 2);
         ctx.assertEquals(context.session.httpCache().size(), 1);
         ctx.assertTrue(context.serverQueue.isEmpty());
         async.countDown();
      });

      test(ctx, context);
   }

   @Test
   public void testEtag(TestContext ctx) {
      Async async = ctx.async();
      Context context = new Context();

      // First request
      context.requests.add(() -> doRequest(context, GET_TEST, null));
      context.serverQueue.add(req -> req.response().putHeader(HttpHeaderNames.ETAG, "\"foo\"").end());
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 1));

      // We have 'bar' and 'foo', should get cached as 'foo' is in the cache
      context.requests.add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\", \"foo\"")));
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 1));

      // We have 'bar' but this is not in the cache yet -> not cached
      context.requests.add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\"")));
      context.serverQueue.add(req -> req.response().putHeader(HttpHeaderNames.ETAG, "\"bar\"").end());
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 2);
         ctx.assertEquals(context.session.httpCache().size(), 2);
      });

      // foo still cached
      context.requests.add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"foo\"")));
      context.handlers.add(() -> ctx.assertEquals(context.serverRequests.get(), 2));

      // bar still cached
      context.requests.add(() -> doRequest(context, GET_TEST, (s, writer) -> writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, "\"bar\"")));
      context.handlers.add(() -> {
         ctx.assertEquals(context.serverRequests.get(), 2);
         async.countDown();
      });

      test(ctx, context);
   }

   private void test(TestContext ctx, Context context) {
      assert !context.requests.isEmpty();
      vertx.createHttpServer().requestHandler(req -> {
         Consumer<HttpServerRequest> handler = context.serverQueue.poll();
         if (handler == null) {
            ctx.fail("No handler for request.");
         }
         context.serverRequests.incrementAndGet();
         handler.accept(req);
         if (!req.response().ended()) {
            ctx.fail(("Response not sent"));
         }
      }).listen(0, "localhost", event -> {
         if (event.failed()) {
            ctx.fail(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               HttpBuilder builder = HttpBuilder.forTesting().host("localhost").port(server.actualPort());
               HttpClientPool client = new HttpClientPoolImpl(1, builder.build(true));
               client.start(result -> {
                  if (result.failed()) {
                     ctx.fail(result.cause());
                     return;
                  }
                  cleanup.add(client::shutdown);
                  context.session = SessionFactory.forTesting(CLOCK);
                  context.pool = client;
                  context.requests.poll().run();
               });
            } catch (Exception e) {
               ctx.fail(e);
            }
         }
      });
   }

   private void doRequest(Context context, Consumer<HttpRequest> configurator, BiConsumer<Session, HttpRequestWriter> headerAppender) {
      HttpRequest request = context.session.httpRequestPool().acquire();
      HttpResponseHandlersImpl handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .onCompletion(s -> {
               Runnable handler = context.handlers.poll();
               if (handler != null) {
                  handler.run();
                  Runnable command = context.requests.poll();
                  if (command == null) {
                     return;
                  }
                  command.run();
               }
            })
            .build();
      configurator.accept(request);
      request.start(handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      @SuppressWarnings("unchecked")
      BiConsumer<Session, HttpRequestWriter>[] appenders = new BiConsumer[]{ headerAppender };
      fireRequest(context.pool, request, headerAppender == null ? null : appenders);
   }

   private void fireRequest(HttpClientPool client, HttpRequest request, BiConsumer<Session, HttpRequestWriter>[] headerAppenders) {
      HttpConnectionPool pool = client.next();
      if (!pool.request(request, headerAppenders, null, false)) {
         pool.executor().schedule(() -> fireRequest(client, request, headerAppenders), 1, TimeUnit.MILLISECONDS);
      }
   }

   private class Context {
      Session session;
      HttpClientPool pool;
      Queue<Runnable> requests = new LinkedList<>();
      Queue<Runnable> handlers = new LinkedList<>();
      AtomicInteger serverRequests = new AtomicInteger();
      Queue<Consumer<HttpServerRequest>> serverQueue = new LinkedList<>();
   }
}
