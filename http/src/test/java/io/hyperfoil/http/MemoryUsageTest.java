package io.hyperfoil.http;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
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
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class MemoryUsageTest {

   @Test
   public void testPlainHttp1x(VertxTestContext context) {
      test(context, new HttpServerOptions().setSsl(false));
   }

   @Test
   public void testEncryptHttp1x(VertxTestContext context) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"))
            .setUseAlpn(true).setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_1_1));
      test(context, serverOptions);
   }

   @Test
   public void testEncryptHttp2(VertxTestContext context) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"))
            .setUseAlpn(true).setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_2));
      test(context, serverOptions);
   }

   protected void test(VertxTestContext context, HttpServerOptions serverOptions) {
      var checkpoint = context.checkpoint(200);
      AtomicLong seenMemoryUsage = new AtomicLong(-1);
      Handler<HttpServer> handler = server -> {
         try {
            HttpClientPool client = client(serverOptions.isSsl() ? Protocol.HTTPS : Protocol.HTTP, server.actualPort());
            client.start(context.succeeding(nil -> {
               Session session = SessionFactory.forTesting();
               HttpRunData.initForTesting(session);
               HttpConnectionPool pool = client.next();
               doRequest(pool, session, context, checkpoint, seenMemoryUsage);
            }));
         } catch (SSLException e) {
            server.close();
            context.failNow(e);
         }
      };
      Vertx.vertx().createHttpServer(serverOptions)
            .requestHandler(ctx -> ctx.response()
                  .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                  .end(Buffer.buffer(new byte[4 * 1024 * 1024])))
            .listen(0, "localhost", context.succeeding(handler));
   }

   private HttpClientPool client(Protocol protocol, int port) throws SSLException {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(protocol).host("localhost").port(port);
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

   private void doRequest(HttpConnectionPool pool, Session session, VertxTestContext context, Checkpoint checkpoint,
         AtomicLong seenMemoryUsage) {
      checkpoint.flag();
      // TODO: I don't know the reason for that but I couldn't refactor using Checkpoint
      // if (async.count() % 100 == 0) {
      //    System.gc();
      // }
      // if (async.count() <= 0) {
      //    return;
      // }
      HttpRequest request = HttpRequestPool.get(session).acquire();
      HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .body(fragmented -> (session1, data, offset, length, isLastPart) -> {
               ByteBufAllocator alloc = data.alloc();
               if (!data.isDirect()) {
                  context.failNow("Expecting to use direct buffers");
               } else if (alloc instanceof PooledByteBufAllocator) {
                  long usedMemory = ((PooledByteBufAllocator) alloc).metric().usedDirectMemory();
                  if (usedMemory < 0) {
                     context.failNow("Cannot fetch direct memory stats");
                  }
                  long seen = seenMemoryUsage.get();
                  if (seen < 0) {
                     seenMemoryUsage.compareAndSet(seen, usedMemory);
                  } else if (usedMemory >= 2 * seen) {
                     context.failNow("Used memory seems to be growing from " + seen + " to " + usedMemory);
                  }
               } else {
                  context.failNow("Buffers are not pooled");
               }
            })
            .onCompletion(s -> pool.executor().schedule(() -> doRequest(pool, session, context, checkpoint, seenMemoryUsage), 1,
                  TimeUnit.MILLISECONDS))
            .build();
      request.path = "/";
      request.method = HttpMethod.GET;
      request.handlers = handlers;
      request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      pool.acquire(false, connection -> request.send(connection, null, true, null));
   }

}
