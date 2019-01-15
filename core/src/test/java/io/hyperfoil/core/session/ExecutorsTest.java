package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ExecutorsTest extends BaseScenarioTest {
   public static final int CLIENT_THREADS = 3;

   @Test
   public void test() {
      Set<Thread> threads = new HashSet<>();
      parallelScenario(10).initialSequence("foo")
            .step(s -> {
               threads.add(Thread.currentThread());
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
               }
               return true;
            })
            .endSequence();

      runScenario();
      assertThat(threads.size()).isEqualTo(CLIENT_THREADS);
   }

   @Override
   protected void initRouter() {
   }

   @Override
   protected int threads() {
      return CLIENT_THREADS;
   }
}
