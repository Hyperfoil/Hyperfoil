package io.hyperfoil.hotrod;

import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HotRodFailuresTest extends BaseHotRodTest {

   @Test
   public void testHotRodFailures() {
      Benchmark benchmark = loadScenario("scenarios/HotRodPutTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      assertTrue(stats.get("example").requestCount > 0);
      assertTrue(stats.get("example").resetCount > 0);
   }

   @Override
   protected void createCache(EmbeddedCacheManager em) {
      ConfigurationBuilder cacheBuilder = new ConfigurationBuilder();
      Cache cache = em.createCache("my-cache", cacheBuilder.build());
      cache.getAdvancedCache().withStorageMediaType().addListener(new ErrorListener());
   }

   @Listener
   public static class ErrorListener {
      public ErrorListener() {
      }

      @CacheEntryCreated
      public void entryCreated(CacheEntryEvent<String, String> event) {
         throw new IllegalStateException("Failed intentionally");
      }
   }
}
