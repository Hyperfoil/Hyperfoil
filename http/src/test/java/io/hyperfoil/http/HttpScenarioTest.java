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
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.util.Util;
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
      HttpServerOptions options = new HttpServerOptions();
      if (useHttps()) {
         options.setSsl(true).setUseAlpn(true).setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      }
      if (useCompression()) {
         options.setCompressionSupported(true);
      }
      benchmarkBuilder.addPlugin(HttpPluginBuilder::new);
      server = vertx.createHttpServer(options).requestHandler(router)
            .listen(0, "localhost", ctx.asyncAssertSuccess(srv -> initWithServer(ctx)));
   }

   // override me
   protected boolean useHttps() {
      return false;
   }

   protected boolean useCompression() {
      return false;
   }

   protected void initWithServer(TestContext ctx) {
      HttpPluginBuilder httpPlugin = benchmarkBuilder.plugin(HttpPluginBuilder.class);
      HttpBuilder http = httpPlugin.http();
      http.protocol(useHttps() ? Protocol.HTTPS : Protocol.HTTP)
          .host("localhost").port(server.actualPort());
      initHttp(http);
   }

   protected void initHttp(HttpBuilder http) {
   }

   protected abstract void initRouter();

   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      String configString = Util.toString(config).replaceAll("http://localhost:8080", "http://localhost:" + server.actualPort());
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(configString, TestUtil.benchmarkData());
      return benchmark;
   }
}
