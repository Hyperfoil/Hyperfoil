package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.steps.ScheduleDelayStep;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PrivatePoolsTest extends BaseScenarioTest {
   @Override
   protected void initRouter() {
      router.get("/").handler(ctx -> ctx.response().end());
   }

   @Test
   public void test(TestContext ctx) {
      Access connection = SessionFactory.access("connection");
      benchmarkBuilder.ergonomics().privateHttpPools(true);
      benchmarkBuilder.http().sharedConnections(2).endHttp().threads(1);
      // We don't need to synchronize since we're using single executor
      Set<Session> runningSessions = new HashSet<>();
      // @formatter:off
      benchmarkBuilder.addPhase("test").always(10).duration(2000).scenario()
            .objectVar("connection")
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/")
                  .handler()
                     .status((request, status) -> {
                        connection.setObject(request.session, request.connection());
                     })
                  .endHandler()
               .endStep()
               .step(SC).thinkTime()
                  .random(ScheduleDelayStep.RandomType.LINEAR)
                  .min(10, TimeUnit.MILLISECONDS).max(100, TimeUnit.MILLISECONDS)
               .endStep()
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/")
                  .headers()
                     .header(HttpHeaderNames.CACHE_CONTROL, "no-cache")
                  .endHeaders()
                  .handler()
                     .status((request, status) -> {
                        ctx.assertEquals(connection.getObject(request.session), request.connection());
                        runningSessions.add(request.session);
                     });
      // @formatter:on
      runScenario();
      // check that the possibly failing handler was called from all sessions
      assertThat(runningSessions.size()).isEqualTo(10);
   }
}
