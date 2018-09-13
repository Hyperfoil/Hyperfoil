package io.sailrocket.core.session;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.Session;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.impl.ConcurrentPoolImpl;
import io.sailrocket.core.impl.PhaseInstanceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class BaseScenarioTest {
   protected final Logger log = LoggerFactory.getLogger(getClass());

   protected Vertx vertx;
   protected HttpClientPool httpClientPool;
   protected Router router;

   public void runScenario(Scenario scenario, int repeats) {
      PhaseInstance phase;
      if (repeats <= 0) {
         phase = new PhaseInstanceImpl.Always(new Phase.Always("test", scenario, 0, Collections.emptyList(), Collections.emptyList(), Long.MAX_VALUE, -1, 1));
      } else {
         phase = new PhaseInstanceImpl.Sequentially(new Phase.Sequentially("test", scenario, 0, Collections.emptyList(), Collections.emptyList(), Long.MAX_VALUE, -1, repeats));
      }
      ReentrantLock statusLock = new ReentrantLock();
      Condition statusCondition = statusLock.newCondition();
      Session session = SessionFactory.create(httpClientPool, phase, 0);
      phase.setComponents(new ConcurrentPoolImpl<>(() -> session), statusLock, statusCondition);
      phase.reserveSessions();
      phase.start(httpClientPool);
      statusLock.lock();
      try {
         while (phase.status() != PhaseInstance.Status.TERMINATED) {
            try {
               if (!statusCondition.await(30, TimeUnit.SECONDS)) {
                  throw new AssertionError("statusCondition timeout");
               }
            } catch (InterruptedException e) {
               throw new AssertionError(e);
            }
         }
      } finally {
         statusLock.unlock();
      }
      if (phase.getError() != null) {
         throw new AssertionError(phase.getError());
      }
   }

   @Before
   public void before(TestContext ctx) throws Exception {
      httpClientPool = HttpClientProvider.netty.builder().host("localhost")
            .protocol(HttpVersion.HTTP_1_1)
            .port(8080)
            .concurrency(3)
            .threads(3)
            .size(100)
            .build();
      Async clientPoolAsync = ctx.async();
      httpClientPool.start(nil -> clientPoolAsync.complete());
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      vertx.createHttpServer().requestHandler(router::accept).listen(8080, "localhost", ctx.asyncAssertSuccess());
   }

   protected abstract void initRouter();

   @After
   public void after(TestContext ctx) {
      httpClientPool.shutdown();
      vertx.close(ctx.asyncAssertSuccess());
   }
}
