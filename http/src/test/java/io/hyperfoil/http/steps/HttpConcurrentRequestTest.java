package io.hyperfoil.http.steps;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.handlers.NewSequenceAction;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.handlers.RangeStatusValidator;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Map;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class HttpConcurrentRequestTest extends HttpScenarioTest {

    private ArrayList<Integer> responsesReceived = new ArrayList<>();

    @Override
    protected void initRouter() {
        router.route().handler(BodyHandler.create());
        router.get("/test").handler(ctx -> {
            responsesReceived.add(Integer.parseInt(ctx.request().getParam("concurrency")));
            ctx.response().setStatusCode(200).end();
        });
    }

       @Test
   public void testConcurrencyZeroWithPipelining() {
      testConcurrencyWithPipelining(0);
   }

    @Test
    public void testConcurrencyOneWithPipelining() {
      testConcurrencyWithPipelining(1);
    }

    @Test
    public void testConcurrencyManyWithPipelining() {
      testConcurrencyWithPipelining(16);
    }

   private void testConcurrencyWithPipelining(int concurrency) {
      int sequenceInvocation = concurrency == 0 ? 1 : concurrency;
      int pipeliningLimit = concurrency == 0 ? 1 : concurrency;
      int requiredSequences = concurrency == 0 ? 2 : 1;
      int maxRequests = concurrency == 0 ? 1 : concurrency;
      // @formatter:off
      scenario()
              .maxSequences(requiredSequences)
              .maxRequests(maxRequests)
              .initialSequence("looper")
                 .step(SC).loop("counter", sequenceInvocation)
                 .steps()
                     .step(SC).action(new NewSequenceAction.Builder().sequence("expectOK"))
              .endSequence()
              .sequence("expectOK")
                  .concurrency(concurrency)
                  .step(SC).httpRequest(HttpMethod.GET)
                        .path("/test?concurrency=${counter}")
                        .handler()
                           .status(new RangeStatusValidator(200, 200))
                        .endHandler()
                     .endStep()
                  .endSequence()
        .endScenario()
     .endPhase()
              .plugin(HttpPluginBuilder.class)
                  .http()
                     .sharedConnections(1)
                     .pipeliningLimit(pipeliningLimit);
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot snapshot = stats.get("expectOK");
      assertThat(snapshot.invalid).isEqualTo(0);
      assertThat(snapshot.connectionErrors).isEqualTo(0);
      assertThat(snapshot.requestCount).isEqualTo(concurrency == 0 ? 1 : concurrency);
      assertThat(responsesReceived.size()).isEqualTo(concurrency == 0 ? 1 : concurrency);
      // this seems counter-intuitive, but despite the sequence is invoked multiple times, we expect
      // the query parameter to be valued just with the last counter value!
      assertThat(responsesReceived).containsOnly(sequenceInvocation);
   }

}
