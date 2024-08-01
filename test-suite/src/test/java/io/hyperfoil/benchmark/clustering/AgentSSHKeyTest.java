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
public class AgentSSHKeyTest extends BaseClusteredTest {

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      startController(ctx);
   }

   /**
    * This test passes an sshKey that doesn't exist to verify it is attempted to be used. We do not verify the key is
    * used in a non error test to not mess with users ssh keys.
    */
   @Test
   public void testSshKeyNotPresent() throws InterruptedException {
      String keyName = "not-present-#@!l";
      Map<String, String> agentOptions = Map.of("sshKey", keyName);
      //@formatter:off
      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
            .name("test")
            .addAgent("agent", "localhost", agentOptions)
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
         assertThat(info.errors).isNotEmpty();
         String errorString = info.errors.get(0);
         assertThat(errorString).containsSubsequence(keyName, "No such file or directory");
      }
   }
}
