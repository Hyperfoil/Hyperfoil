package io.hyperfoil.core.http;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.Protocol;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.HttpResponseHandlersImpl;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class MemoryUsageTest {
   private static final BiConsumer[] NO_APPENDERS = new BiConsumer[]{ (session, writer) -> { } };

   @Test
   public void testPlainHttp1x(TestContext context) {
      test(context, new HttpServerOptions().setSsl(false));
   }

   @Test
   public void testEncryptHttp1x(TestContext context) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"))
            .setUseAlpn(true).setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_1_1));
      test(context, serverOptions);
   }

   @Test
   public void testEncryptHttp2(TestContext context) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"))
            .setUseAlpn(true).setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_2));
      test(context, serverOptions);
   }

   protected void test(TestContext context, HttpServerOptions serverOptions) {
      Async async = context.async(200);
      AtomicLong seenMemoryUsage = new AtomicLong(-1);
      Handler<HttpServer> handler = server -> {
         try {
            HttpClientPool client = client(serverOptions.isSsl() ? Protocol.HTTPS : Protocol.HTTP, server.actualPort());
            client.start(context.asyncAssertSuccess(nil -> {
               Session session = SessionFactory.forTesting();
               HttpConnectionPool pool = client.next();
               doRequest(pool, session, context, async, seenMemoryUsage);
            }));
         } catch (SSLException e) {
            server.close();
            context.fail(e);
         }
      };
      Vertx.vertx().createHttpServer(serverOptions)
            .requestHandler(ctx -> ctx.response()
                  .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                  .end(Buffer.buffer(new byte[4 * 1024 * 1024])))
            .listen(0, "localhost", context.asyncAssertSuccess(handler));
   }

   private HttpClientPool client(Protocol protocol, int port) throws SSLException {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(protocol).host("localhost").port(port);
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

   private void doRequest(HttpConnectionPool pool, Session session, TestContext context, Async async, AtomicLong seenMemoryUsage) {
      async.countDown();
      if (async.count() % 100 == 0) {
         System.gc();
      }
      if (async.count() <= 0) {
         return;
      }
      HttpRequest request = session.httpRequestPool().acquire();
      HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .body(fragmented -> (session1, data, offset, length, isLastPart) -> {
               ByteBufAllocator alloc = data.alloc();
               if (!data.isDirect()) {
                  context.fail("Expecting to use direct buffers");
               } else if (alloc instanceof PooledByteBufAllocator) {
                  long usedMemory = ((PooledByteBufAllocator) alloc).metric().usedDirectMemory();
                  if (usedMemory < 0) {
                     context.fail("Cannot fetch direct memory stats");
                  }
                  long seen = seenMemoryUsage.get();
                  if (seen < 0) {
                     seenMemoryUsage.compareAndSet(seen, usedMemory);
                  } else if (usedMemory >= 2 * seen) {
                     context.fail(async.count() + ": Used memory seems to be growing from " + seen + " to " + usedMemory);
                  }
               } else {
                  context.fail("Buffers are not pooled");
               }
            })
            .onCompletion(s -> pool.executor().schedule(() -> doRequest(pool, session, context, async, seenMemoryUsage), 1, TimeUnit.MILLISECONDS))
            .build();
      request.path = "/";
      request.method = HttpMethod.GET;
      request.handlers = handlers;
      request.start(handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      fireRequest(pool, request);
   }

   private void fireRequest(HttpConnectionPool pool, HttpRequest request) {
      if (!pool.request(request, NO_APPENDERS, true, null, false)) {
         pool.executor().schedule(() -> fireRequest(pool, request), 1, TimeUnit.MILLISECONDS);
      }
   }

}
