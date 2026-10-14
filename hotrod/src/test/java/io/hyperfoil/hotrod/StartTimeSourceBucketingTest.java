package io.hyperfoil.hotrod;

import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.core.statistics.OpenModelBucketingContract;
import io.hyperfoil.hotrod.config.HotRodPluginBuilder;
import io.hyperfoil.hotrod.steps.HotRodRequestBuilder;

/**
 * Tests that statistics are bucketed correctly for HotRod.
 * Uses the shared OpenModelBucketingContract scenario.
 */
public class StartTimeSourceBucketingTest extends BaseHotRodTest
      implements OpenModelBucketingContract {

   /** Uses 1 thread to ensure the stall actually limits throughput. */
   @Override
   protected int threads() {
      return 1;
   }

   @Override
   protected void createCache(EmbeddedCacheManager em) {
      em.administration().withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
            .getOrCreateCache("default", new ConfigurationBuilder().build());
   }

   @Override
   public BenchmarkBuilder benchmarkBuilder() {
      return benchmarkBuilder;
   }

   @Override
   public void configureProtocol() {
      HotRodPluginBuilder hotRodPlugin = benchmarkBuilder.addPlugin(HotRodPluginBuilder::new);
      hotRodPlugin.addCluster()
            .uri("hotrod://127.0.0.1:" + hotrodServers[0].getPort())
            .addCache("default");
      hotRodPlugin.ergonomics().compensateInternalLatency(true);
   }

   @Override
   public void addRequestStep(SequenceBuilder sequence) {
      sequence.stepBuilder(new HotRodRequestBuilder()
            .put("default")
            .key("test-key")
            .value("test-value"));
   }
}
