package io.hyperfoil.benchmark.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

@Tag("io.hyperfoil.test.Benchmark")
public class MoreAgentsThanUsersTest extends BaseClusteredTest {
   private static final String TRACE_CONFIG = "-Dlog4j.configurationFile=file://" +
         MoreAgentsThanUsersTest.class.getProtectionDomain().getCodeSource().getLocation().getPath() +
         "/../../src/test/resources/log4j2.xml";

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      startController(ctx);
   }

   @Test
   public void test() throws InterruptedException {
      Map<String, String> agentOptions = System.getProperty("agent.log.trace") != null ? Map.of("extras", TRACE_CONFIG) : null;
      //@formatter:off
      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
            .name("test")
            .addAgent("a1", "localhost", agentOptions)
            .addAgent("a2", "localhost", agentOptions)
            .threads(2)
            .addPlugin(HttpPluginBuilder::new)
               .http()
                  .host("localhost").port(httpServer.actualPort())
                  .sharedConnections(4)
               .endHttp()
            .endPlugin()
            .addPhase("test").always(1)
            .duration(1000)
            .scenario()
               .initialSequence("test")
                  .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET).path("/").endStep()
               .endSequence()
            .endScenario()
            .endPhase();
      //@formatter:on

      try (RestClient client = new RestClient(vertx, "localhost", controllerPort, false, false, null)) {
         Client.BenchmarkRef ref = client.register(benchmark.build(), null);
         Client.RunRef run = ref.start(null, Collections.emptyMap());

         Run info;
         do {
            info = run.get();
            Thread.sleep(100);
         } while (!info.completed);
         assertThat(info.errors).isEmpty();
      }
   }
}
