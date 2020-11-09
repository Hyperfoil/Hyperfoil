package io.hyperfoil.benchmark.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.test.Benchmark;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
@Category(Benchmark.class)
public class MoreAgentsThanUsersTest extends BaseClusteredTest {

   @Before
   public void before(TestContext ctx) {
      super.before(ctx);
      startController(ctx);
   }

   @Test
   public void test() throws InterruptedException {
      //@formatter:off
      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
            .name("test")
            .addAgent("a1", "localhost", null)
            .addAgent("a2", "localhost", null)
            .threads(2)
            .http()
               .host("localhost").port(httpServer.actualPort())
               .sharedConnections(4)
            .endHttp()
            .addPhase("test").always(1)
            .duration(1000)
            .scenario()
               .initialSequence("test")
                  .step(StepCatalog.SC).httpRequest(HttpMethod.GET).path("/").endStep()
               .endSequence()
            .endScenario()
            .endPhase();
      //@formatter:on

      RestClient client = new RestClient(vertx, "localhost", 8090);
      Client.BenchmarkRef ref = client.register(benchmark.build(), null);
      Client.RunRef run = ref.start(null);

      Run info;
      do {
         info = run.get();
         Thread.sleep(100);
      } while (!info.completed);
      assertThat(info.errors).isEmpty();
   }
}
