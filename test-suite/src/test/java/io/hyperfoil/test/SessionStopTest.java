package io.hyperfoil.test;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class SessionStopTest extends BaseHttpScenarioTest {
   @Override
   protected void initRouter() {
      router.get("/test").handler(ctx -> ctx.response().end("OK"));
   }

   @Test
   public void testStopAsStep() {
      AtomicInteger counter = new AtomicInteger();
      parallelScenario(1).initialSequence("test")
            .step(SC).stop()
            .step(s -> {
               counter.incrementAndGet();
               return true;
            });
      runScenario();
      assertThat(counter.get()).isEqualTo(0);
   }

   @Test
   public void testStopAsHandler() {
      AtomicInteger counter = new AtomicInteger();
      parallelScenario(1).initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET).path("/test")
            .handler().onCompletion(Session::stop).endHandler()
            .endStep()
            .step(s -> {
               counter.incrementAndGet();
               return true;
            });
      runScenario();
      assertThat(counter.get()).isEqualTo(0);
   }
}
