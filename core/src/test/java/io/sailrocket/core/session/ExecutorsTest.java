package io.sailrocket.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.core.builders.ScenarioBuilder;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ExecutorsTest extends BaseScenarioTest {

   @Test
   public void test() {
      Set<Thread> threads = new HashSet<>();
      ScenarioBuilder scenario = scenarioBuilder().initialSequence("foo")
            .step(s -> {
               threads.add(Thread.currentThread());
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
               }
               return true;
            })
            .endSequence();

      runScenarioOnceParallel(scenario, 10);
      assertThat(threads.size()).isEqualTo(CLIENT_THREADS);
   }

   @Override
   protected void initRouter() {
   }
}
