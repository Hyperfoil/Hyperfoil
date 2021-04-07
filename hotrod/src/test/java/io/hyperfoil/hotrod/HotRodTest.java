package io.hyperfoil.hotrod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HotRodTest extends BaseHotRodTest {

   @Test
   public void testHotRodPut() {
      Benchmark benchmark = loadScenario("scenarios/HotRodPutTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      assertTrue(stats.get("example").requestCount > 0);
      assertEquals(0, stats.get("example").connectionErrors);
   }

   @Test
   public void testHotRodGet() {
      Benchmark benchmark = loadScenario("scenarios/HotRodTestGet.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      assertTrue(stats.get("example").requestCount > 0);
      assertEquals(0, stats.get("example").connectionErrors);
   }

   @Override
   protected void createCache(EmbeddedCacheManager em) {
      ConfigurationBuilder cacheBuilder = new ConfigurationBuilder();
      em.createCache("my-cache", cacheBuilder.build());
   }
}
