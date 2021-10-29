package io.hyperfoil.hotrod;

import java.io.IOException;
import java.io.InputStream;

import org.assertj.core.util.Files;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.commons.test.TestResourceTracker;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.core.admin.embeddedserver.EmbeddedServerAdminOperationHandler;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.junit.After;
import org.junit.Before;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.impl.Util;
import io.vertx.ext.unit.TestContext;

public abstract class BaseHotRodTest extends BaseScenarioTest {

   protected HotRodServer[] hotrodServers;
   private int numberServers = 1;

   @Before
   public void before(TestContext ctx) {
      super.before(ctx);
      TestResourceTracker.setThreadTestName("hyperfoil-HotRodTest");
      hotrodServers = new HotRodServer[numberServers];
      for (int i = 0; i < numberServers; i++) {

         GlobalConfigurationBuilder globalBuilder = new GlobalConfigurationBuilder();
         globalBuilder.globalState().persistentLocation(Files.temporaryFolder().getPath());
         globalBuilder.globalState().enabled(true);
         globalBuilder.security().authorization().disable();

         HotRodServerConfigurationBuilder serverBuilder = new HotRodServerConfigurationBuilder();
         serverBuilder.adminOperationsHandler(new EmbeddedServerAdminOperationHandler());

         EmbeddedCacheManager em = new DefaultCacheManager(globalBuilder.build());
         createCache(em);

         hotrodServers[i] = HotRodClientTestingUtil.startHotRodServer(em, serverBuilder);
      }
   }

   protected abstract void createCache(EmbeddedCacheManager em);

   @After
   public void after(TestContext ctx) {
      super.after(ctx);
      if (hotrodServers != null) {
         for (HotRodServer hotRodServer : hotrodServers) {
            hotRodServer.stop();
         }
      }
   }

   @Override
   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      String configString = Util.toString(config)
            .replaceAll("hotrod://localhost:11222", "hotrod://localhost:" + hotrodServers[0].getPort());
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(configString, TestUtil.benchmarkData());
      return benchmark;
   }
}
