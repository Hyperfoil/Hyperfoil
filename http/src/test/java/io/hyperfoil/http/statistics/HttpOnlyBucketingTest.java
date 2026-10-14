package io.hyperfoil.http.statistics;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.NavigableMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.statistics.BucketInvariants;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.junit5.VertxExtension;

/**
 * HTTP-specific bucketing tests.
 *
 * Tests features that only exist in HTTP (not HotRod), such as
 * connection pool Blocked Time and Closed Model Compensation.
 */
@ExtendWith(VertxExtension.class)
public class HttpOnlyBucketingTest extends BaseHttpScenarioTest {

   private static final int TARGET_RATE = 10;
   private static final long DURATION_MS = 3000;
   private static final String PLAIN = "plain";
   private static final String COMPENSATED = "compensated";

   @Override
   protected void initRouter() {
      router.get("/slow250").handler(ctx -> vertx.setTimer(250, id -> ctx.response().setStatusCode(200).end()));
      router.get("/slow1500").handler(ctx -> vertx.setTimer(1500, id -> ctx.response().setStatusCode(200).end()));
   }

   /** Uses 1 thread so the connection wait isn't hidden by another thread. */
   @Override
   protected int threads() {
      return 1;
   }

   /**
    * Tests that blocked time goes to the same bucket as the request itself.
    */
   @Test
   public void blockedTimeSharesTheBucketOfTheRequestItBlocked() {
      // A single connection shared by both phases: whichever loses the race waits ~1.5s for it,
      // well over the sampling period.
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL).sharedConnections(1);
      for (String phase : List.of("first", "second")) {
         benchmarkBuilder.addPhase(phase).atOnce(1).scenario().initialSequence(phase)
               .step(SC).httpRequest(HttpMethod.GET).path("/slow1500").endStep();
      }

      BucketInvariants collected = BucketInvariants.collect(benchmarkBuilder.build());
      collected.dump(log);
      collected.assertOperationsAreNotSplit(true);

      assertThat(totalResponses(collected)).as("both requests should have completed").isEqualTo(2);
      // Exactly one phase loses the race and blocks. If neither did there is nothing to assert
      // about bucket alignment, so fail rather than pass vacuously.
      long phasesThatBlocked = collected.series().values().stream()
            .filter(buckets -> buckets.values().stream().anyMatch(s -> s.blockedTime > 0)).count();
      assertThat(phasesThatBlocked).as("one phase must have waited for the shared connection").isEqualTo(1);
   }

   /**
    * Tests that closed model compensation metrics use the actual fire time.
    */
   @Test
   public void closedModelCompensationIsFiledWhenTheIterationActuallyFired() {
      // One user, one intended iteration per 100ms, but the server needs 250ms: every iteration
      // falls a further 150ms behind.
      // @formatter:off
      benchmarkBuilder.addPhase("test").always(1)
            .duration(DURATION_MS)
            .scenario()
            .initialSequence("request")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/slow250")
                  .metric(PLAIN)
                  .compensation()
                     .targetRate(TARGET_RATE)
                     .metric(COMPENSATED)
                     .end()
                  .endStep()
            .endSequence();
      // @formatter:on

      BucketInvariants collected = BucketInvariants.collect(benchmarkBuilder.build());
      collected.dump(log);
      collected.assertOperationsAreNotSplit(false);

      NavigableMap<Long, StatisticsSnapshot> plain = collected.buckets("test", PLAIN);
      NavigableMap<Long, StatisticsSnapshot> compensated = collected.buckets("test", COMPENSATED);

      assertThat(compensated.keySet()).as("compensated samples follow the achieved timeline").isEqualTo(plain.keySet());
      plain.forEach((bucket, plainStats) -> {
         StatisticsSnapshot compensatedStats = compensated.get(bucket);
         assertThat(compensatedStats.requestCount).as("bucket %d request count", bucket).isEqualTo(plainStats.requestCount);
         assertThat(compensatedStats.histogram.getMean())
               .as("bucket %d: compensated latency includes the queueing the plain metric omits", bucket)
               .isGreaterThan(plainStats.histogram.getMean());
      });

      List<Long> compensatedMeans = compensated.values().stream()
            .map(s -> (long) (s.histogram.getMean() / 1_000_000)).toList();
      assertThat(compensatedMeans).as("falling further behind each iteration must show as growing latency")
            .isSorted().doesNotHaveDuplicates();
   }

   /**
    * Counts responses rather than requests: the bucketing invariants only require that a bucket's
    * outcomes balance its requests, so counting requests would let a run in which both of them
    * failed pass vacuously.
    */
   private static long totalResponses(BucketInvariants collected) {
      return collected.series().values().stream().flatMap(b -> b.values().stream())
            .mapToLong(s -> s.responseCount).sum();
   }

   private HttpBuilder http() {
      return benchmarkBuilder.plugin(HttpPluginBuilder.class).http();
   }
}
