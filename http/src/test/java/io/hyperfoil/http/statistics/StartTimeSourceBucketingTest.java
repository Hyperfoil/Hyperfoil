package io.hyperfoil.http.statistics;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;

import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.core.statistics.OpenModelBucketingContract;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.junit5.VertxExtension;

/**
 * Tests that statistics are bucketed correctly for HTTP requests.
 * HTTP-specific features are tested separately in HttpOnlyBucketingTest.
 */
@ExtendWith(VertxExtension.class)
public class StartTimeSourceBucketingTest extends BaseHttpScenarioTest
      implements OpenModelBucketingContract {

   @Override
   protected void initRouter() {
      router.get("/ping").handler(ctx -> ctx.response().setStatusCode(200).end());
   }

   @Override
   protected int threads() {
      return 1;
   }

   private HttpBuilder http() {
      return benchmarkBuilder.plugin(HttpPluginBuilder.class).http();
   }

   @Override
   public BenchmarkBuilder benchmarkBuilder() {
      return benchmarkBuilder;
   }

   @Override
   public void configureProtocol() {
      benchmarkBuilder.plugin(HttpPluginBuilder.class).ergonomics().compensateInternalLatency(true);
      // Provide enough connections so the pool itself doesn't become a bottleneck.
      http().sharedConnections(TARGET_RATE * 5);
   }

   @Override
   public void addRequestStep(SequenceBuilder sequence) {
      sequence.step(SC).httpRequest(HttpMethod.GET).path("/ping").endStep();
   }
}
