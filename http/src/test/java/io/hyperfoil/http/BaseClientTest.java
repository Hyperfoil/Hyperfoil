package io.hyperfoil.http;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

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
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

public class BaseClientTest extends VertxBaseTest {
   protected static final List<HttpVersion> HTTP1x_ONLY = Collections.singletonList(HttpVersion.HTTP_1_1);
   protected static final List<HttpVersion> HTTP2_ONLY = Collections.singletonList(HttpVersion.HTTP_2);

   protected void server(boolean ssl, List<HttpVersion> serverVersions, Handler<HttpServerRequest> requestHandler,
         Handler<AsyncResult<HttpServer>> listenHandler) {
      HttpServer httpServer;
      if (ssl) {
         JksOptions keyStoreOptions = new JksOptions().setPath("keystore.jks").setPassword("test123");
         HttpServerOptions httpServerOptions = new HttpServerOptions()
               .setSsl(true)
               .setKeyStoreOptions(keyStoreOptions)
               .setUseAlpn(true)
               .setAlpnVersions(serverVersions);
         httpServer = vertx.createHttpServer(httpServerOptions);
         httpServer.requestHandler(requestHandler).listen(0, "localhost", listenHandler);
      } else {
         httpServer = vertx.createHttpServer();
         httpServer.requestHandler(requestHandler).listen(0, "localhost", listenHandler);
      }
   }

   protected HttpClientPool client(int port, boolean ssl, io.hyperfoil.http.api.HttpVersion[] versions) throws Exception {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(ssl ? Protocol.HTTPS : Protocol.HTTP).host("localhost").port(port);
      builder.allowHttp2(Stream.of(versions).anyMatch(v -> v == io.hyperfoil.http.api.HttpVersion.HTTP_2_0));
      builder.allowHttp1x(Stream.of(versions).anyMatch(v -> v == io.hyperfoil.http.api.HttpVersion.HTTP_1_1));
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

   protected void test(TestContext ctx, boolean ssl, io.hyperfoil.http.api.HttpVersion[] clientVersions,
         List<HttpVersion> serverVersions, Handler<HttpServerRequest> requestHandler, ClientAction clientAction) {
      Async async = ctx.async();
      server(ssl, serverVersions, requestHandler, event -> {
         if (event.failed()) {
            ctx.fail(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               HttpClientPool client = client(server.actualPort(), ssl, clientVersions);
               client.start(result -> {
                  if (result.failed()) {
                     ctx.fail(result.cause());
                     return;
                  }
                  cleanup.add(client::shutdown);
                  clientAction.run(client, async);
               });
            } catch (Exception e) {
               ctx.fail(e);
            }
         }
      });
   }

   protected void test(TestContext ctx, boolean ssl, io.hyperfoil.http.api.HttpVersion[] clientVersions,
         List<HttpVersion> serverVersions, Handler<HttpServerRequest> requestHandler,
         Handler<AsyncResult<Void>> clientStartHandler) {
      server(ssl, serverVersions, requestHandler, event -> {
         if (event.failed()) {
            ctx.fail(event.cause());
         } else {
            HttpServer server = event.result();
            cleanup.add(server::close);
            try {
               HttpClientPool client = client(server.actualPort(), ssl, clientVersions);
               client.start(clientStartHandler);
            } catch (Exception e) {
               ctx.fail(e);
            }
         }
      });
   }

   protected void sendRequestAndAssertStatus(TestContext ctx, HttpClientPool client, Async async, HttpMethod method,
         String path, int expectedStatus) {
      Session session = SessionFactory.forTesting();
      HttpRunData.initForTesting(session);
      HttpRequest request = HttpRequestPool.get(session).acquire();
      AtomicBoolean statusReceived = new AtomicBoolean(false);
      HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .status((r, status) -> {
               if (status != expectedStatus) {
                  ctx.fail();
               } else {
                  statusReceived.set(true);
               }
            })
            .onCompletion(s -> {
               if (statusReceived.get()) {
                  async.complete();
               } else {
                  ctx.fail("Status was not received.");
               }
            }).build();
      request.method = method;
      request.path = path;

      HttpConnectionPool pool = client.next();
      request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      pool.acquire(false, c -> request.send(c, null, true, null));
   }

   @FunctionalInterface
   interface ClientAction {
      void run(HttpClientPool client, Async async);
   }
}
