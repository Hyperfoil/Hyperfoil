package io.hyperfoil.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.impl.Util;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public abstract class BaseHttpScenarioTest extends BaseScenarioTest {

   protected Vertx vertx;
   protected Router router;
   protected HttpServer server;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before();
      this.vertx = vertx;
      router = Router.router(vertx);
      initRouter();
      benchmarkBuilder.addPlugin(HttpPluginBuilder::new);
      var serverLatch = new CountDownLatch(1);
      startServer(ctx).onComplete(ctx.succeedingThenComplete()).onComplete(event -> {
         if (event.succeeded()) {
            serverLatch.countDown();
         }
      });
      try {
         serverLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         ctx.failNow(e);
      }
   }

   protected Future<Void> startServer(VertxTestContext ctx) {
      boolean tls = useHttps();
      boolean compression = useCompression();
      HttpServerOptions options = new HttpServerOptions();
      if (tls) {
         options.setSsl(true).setUseAlpn(true)
               .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      }
      if (compression) {
         options.setCompressionSupported(true);
      }
      Promise<Void> promise = Promise.promise();
      server = vertx.createHttpServer(options)
            .requestHandler(router)
            .listen(0, "localhost", ctx.succeeding(srv -> {
               initWithServer(srv, tls);
               promise.complete();
               ctx.completeNow();
            }));
      return promise.future();
   }

   // override me
   protected boolean useHttps() {
      return false;
   }

   protected boolean useCompression() {
      return false;
   }

   protected void initWithServer(HttpServer srv, boolean tls) {
      HttpPluginBuilder httpPlugin = benchmarkBuilder.plugin(HttpPluginBuilder.class);
      HttpBuilder http = httpPlugin.http();
      http.protocol(tls ? Protocol.HTTPS : Protocol.HTTP)
            .name("myhost")
            .host("localhost").port(srv.actualPort());
      initHttp(http);
   }

   protected void initHttp(HttpBuilder http) {
   }

   protected abstract void initRouter();

   @Override
   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(
            config, TestUtil.benchmarkData(), Map.of("PORT", String.valueOf(server.actualPort())));
   }

   protected void serveResourceChunked(io.vertx.ext.web.RoutingContext ctx, String resource) {
      try {
         InputStream index = getClass().getClassLoader().getResourceAsStream(resource);
         String html = Util.toString(index);
         // We'll send the body in two chunks to make sure the code works even if the body is not delivered in one row
         ctx.response().setChunked(true);
         int bodyStartIndex = html.indexOf("<body>");
         ctx.response().write(html.substring(0, bodyStartIndex), result -> vertx.setTimer(100, ignores -> {
            ctx.response().write(html.substring(bodyStartIndex));
            ctx.response().end();
         }));
      } catch (IOException e) {
         ctx.response().setStatusCode(500).end();
      }
   }
}
