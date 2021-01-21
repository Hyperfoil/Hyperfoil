package io.hyperfoil.core.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Protocol;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.HttpVersion;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.core.VertxBaseTest;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.HttpResponseHandlersImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HttpDecodeSpaceTest extends VertxBaseTest {
   private static final List<io.vertx.core.http.HttpVersion> HTTP1x_ONLY = Collections.singletonList(io.vertx.core.http.HttpVersion.HTTP_1_1);
   private static final List<io.vertx.core.http.HttpVersion> HTTP2_ONLY = Collections.singletonList(io.vertx.core.http.HttpVersion.HTTP_2);

   @Test
   public void testSimpleHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/ping");
   }

   @Test
   public void testSimpleHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/ping");
   }

   @Test
   public void testSpaceBeforeHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceBeforeHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceAfterHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceAfterHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceBeforeAfterHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpaceBeforeAfterHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpacesHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testSpacesHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testComplexHttp1x(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   @Test
   public void testComplexHttp2(TestContext ctx) throws Exception {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   private void test(TestContext ctx, boolean ssl, HttpVersion[] clientVersions, List<io.vertx.core.http.HttpVersion> serverVersions, String path) throws Exception {
      Async async = ctx.async();
      server(ssl, serverVersions, event -> {
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
                  Session session = SessionFactory.forTesting();
                  HttpRequest request = session.httpRequestPool().acquire();
                  AtomicBoolean statusReceived = new AtomicBoolean(false);
                  HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
                        .status((r, status) -> {
                           if (status != 200) {
                              ctx.fail("Status Code : " + status);
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
                  request.method = HttpMethod.GET;
                  request.path = path;
                  request.start(handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));

                  client.next().request(request, null, true, null, false);
               });
            } catch (Exception e) {
               ctx.fail(e);
            }
         }
      });
   }

   private HttpClientPool client(int port, boolean ssl, HttpVersion[] versions) throws Exception {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(ssl ? Protocol.HTTPS : Protocol.HTTP).host("localhost").port(port);
      builder.allowHttp2(Stream.of(versions).anyMatch(v -> v == HttpVersion.HTTP_2_0));
      builder.allowHttp1x(Stream.of(versions).anyMatch(v -> v == HttpVersion.HTTP_1_1));
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

   private void server(boolean ssl, List<io.vertx.core.http.HttpVersion> serverVersions, Handler<AsyncResult<HttpServer>> handler) {
      HttpServer httpServer;
      if (ssl) {
         JksOptions keyStoreOptions = new JksOptions().setPath("keystore.jks").setPassword("test123");
         HttpServerOptions httpServerOptions = new HttpServerOptions()
               .setSsl(true)
               .setKeyStoreOptions(keyStoreOptions)
               .setUseAlpn(true)
               .setAlpnVersions(serverVersions);
         httpServer = vertx.createHttpServer(httpServerOptions);
         httpServer.requestHandler(HttpDecodeSpaceTest::isPathCorrect).listen(0, "localhost", handler);
      } else {
         httpServer = vertx.createHttpServer();
         httpServer.requestHandler(HttpDecodeSpaceTest::isPathCorrect).listen(0, "localhost", handler);
      }
   }

   private static void isPathCorrect(HttpServerRequest req) {
      int status = -1;
      if (req.path().contains(" ")) {
          status = 600;
      } else {
          int question = req.path().indexOf("?");
          String subFirst = "";
          String subSecond = "";
          if (question != -1) {
              subFirst = req.path().substring(0 , question);
              subSecond = req.path().substring(req.path().lastIndexOf("?") + 1);
              if (subFirst.contains("+")) {
                  status = 601;
              } else if (subFirst.contains("%20")) {
                  status = 200;
              } else {
                  status = 200;
              }
              if (status == 200) {
                  if (subSecond.contains("%20")) {
                      status = 602;
                  } else if (subSecond.contains("+")) {
                      status = 200;
                  } else {
                      status = 200;
                  }
              }
          }
      }
      req.response().setStatusCode(status).end("Hello");
   }
}
