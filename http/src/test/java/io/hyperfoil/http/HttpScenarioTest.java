package io.hyperfoil.http;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
public abstract class HttpScenarioTest extends BaseScenarioTest {
   protected Router router;
   protected HttpServer server;

   @Before
   public void before(TestContext ctx) {
      super.before(ctx);
      router = Router.router(vertx);
      initRouter();
      benchmarkBuilder.addPlugin(HttpPluginBuilder::new);
      startServer(ctx, useHttps(), useCompression());
   }

   protected Future<Void> startServer(TestContext ctx, boolean tls, boolean compression) {
      HttpServerOptions options = new HttpServerOptions();
      if (tls) {
         options.setSsl(true).setUseAlpn(true).setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      }
      if (compression) {
         options.setCompressionSupported(true);
      }
      Promise<Void> promise = Promise.promise();
      server = vertx.createHttpServer(options).requestHandler(router)
            .listen(0, "localhost", ctx.asyncAssertSuccess(srv -> {
               initWithServer(tls);
               promise.complete();
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

   protected void initWithServer(boolean tls) {
      HttpPluginBuilder httpPlugin = benchmarkBuilder.plugin(HttpPluginBuilder.class);
      HttpBuilder http = httpPlugin.http();
      http.protocol(tls ? Protocol.HTTPS : Protocol.HTTP)
            .host("localhost").port(server.actualPort());
      initHttp(http);
   }

   protected void initHttp(HttpBuilder http) {
   }

   protected abstract void initRouter();

   @Override
   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      String configString = Util.toString(config).replaceAll("http://localhost:8080", "http://localhost:" + server.actualPort());
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(configString, TestUtil.benchmarkData());
      return benchmark;
   }
}
