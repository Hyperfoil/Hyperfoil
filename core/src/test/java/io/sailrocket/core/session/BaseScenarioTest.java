package io.sailrocket.core.session;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.config.Scenario;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.builders.HttpBuilder;
import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.client.netty.HttpClientPoolImpl;
import io.sailrocket.core.impl.ConcurrentPoolImpl;
import io.sailrocket.core.impl.PhaseInstanceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

public abstract class BaseScenarioTest {
   protected final Logger log = LoggerFactory.getLogger(getClass());

   protected Vertx vertx;
   protected Router router;
   private BenchmarkBuilder benchmarkBuilder;
   private Benchmark benchmark;

   protected List<Session> runScenario(ScenarioBuilder scenarioBuilder, int repeats) {
      assert benchmarkBuilder == scenarioBuilder.endScenario().endPhase().endSimulation();
      benchmark = benchmarkBuilder.build();
      Scenario scenario = benchmark.simulation().phases().iterator().next().scenario();
      PhaseInstance phase;
      if (repeats <= 0) {
         phase = new PhaseInstanceImpl.Always(new Phase.Always("test", scenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Long.MAX_VALUE, -1, "test", 1));
      } else {
         phase = new PhaseInstanceImpl.Sequentially(new Phase.Sequentially("test", scenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Long.MAX_VALUE, -1, "test", repeats));
      }
      return runScenario(phase);
   }

   protected List<Session> runScenarioOnceParallel(ScenarioBuilder scenarioBuilder, int concurrency) {
      assert benchmarkBuilder == scenarioBuilder.endScenario().endPhase().endSimulation();
      benchmark = benchmarkBuilder.build();
      Scenario scenario = benchmark.simulation().phases().iterator().next().scenario();
      PhaseInstance phase = new PhaseInstanceImpl.AtOnce(new Phase.AtOnce("test", scenario, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Long.MAX_VALUE, -1, "test", concurrency));
      return runScenario(phase);
   }

   private List<Session> runScenario(PhaseInstance phase) {
      CountDownLatch latch = new CountDownLatch(1);
      List<Session> sessionList = Collections.synchronizedList(new ArrayList<>());
      HttpClientPool httpClientPool;
      try {
         httpClientPool = new HttpClientPoolImpl(threads(), benchmark.simulation().http());
      } catch (SSLException e) {
         throw new RuntimeException(e);
      }
      httpClientPool.start(() -> {
         phase.setComponents(new ConcurrentPoolImpl<>(() -> {
            Session session = SessionFactory.create(phase.definition().scenario, 0);
            sessionList.add(session);
            session.attach(httpClientPool.next());
            return session;
         }), sessionList, (p, status) -> {
            if (status == PhaseInstance.Status.TERMINATED) {
               latch.countDown();
               ;
            }
         });
         phase.reserveSessions();
         phase.start(httpClientPool.executors());
      });
      try {
         if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("statusCondition timeout");
         }
      } catch (InterruptedException e) {
         throw new AssertionError(e);
      }
      if (phase.getError() != null) {
         throw new AssertionError(phase.getError());
      }
      httpClientPool.shutdown();
      return sessionList;
   }

   @Before
   public void before(TestContext ctx) {
      benchmarkBuilder = BenchmarkBuilder.builder();
      benchmarkBuilder.simulation().http().baseUrl("http://localhost:8080");

      initHttp(benchmarkBuilder.simulation().http());
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      vertx.createHttpServer().requestHandler(router::accept).listen(8080, "localhost",
            ctx.asyncAssertSuccess());
   }

   protected void initHttp(HttpBuilder http) {
   }

   protected abstract void initRouter();

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }

   protected ScenarioBuilder scenarioBuilder() {
      return benchmarkBuilder.simulation().addPhase("test").atOnce(1).duration(1).scenario();
   }

   protected int threads() {
      return 3;
   }
}
