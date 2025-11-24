package io.hyperfoil.hotrod;

import static org.infinispan.commons.test.Exceptions.assertException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class HotRodTest extends BaseHotRodTest {

   @Test
   public void testHotRodPut() {
      Benchmark benchmark = loadScenario("scenarios/HotRodPutTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot statsExample = stats.get("example");
      assertTrue(statsExample.requestCount > 0);
      assertEquals(0, statsExample.connectionErrors);
   }

   @Test
   public void testHotRodGet() {
      Benchmark benchmark = loadScenario("scenarios/HotRodTestGet.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      assertTrue(stats.get("example").requestCount > 0);
      assertEquals(0, stats.get("example").connectionErrors);
   }

   @Test
   public void testUndefinedCache() throws Exception {
      try (InputStream is = getClass().getClassLoader().getResourceAsStream("scenarios/HotRodPutTest.hf.yaml")) {
         String cacheName = "something-else-undefined";
         Benchmark benchmark = loadBenchmark(is,
               Map.of("CACHE", cacheName, "PORT", String.valueOf(hotrodServers[0].getPort())));

         RuntimeException e = assertThrows(RuntimeException.class, () -> runScenario(benchmark));
         assertException(RuntimeException.class, IllegalArgumentException.class,
               String.format("Cache '%s' is not a defined cache", cacheName), e);
      }
   }

   @Override
   protected void createCache(EmbeddedCacheManager em) {
      ConfigurationBuilder cacheBuilder = new ConfigurationBuilder();
      em.createCache("my-cache", cacheBuilder.build());
   }
}
