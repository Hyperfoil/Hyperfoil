package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class ExecutorsTest extends BaseScenarioTest {
   public static final int CLIENT_THREADS = 3;

   @Test
   public void test() {
      Set<Thread> threads = new HashSet<>();
      parallelScenario(10).initialSequence("foo")
            .step(s -> {
               synchronized (threads) {
                  threads.add(Thread.currentThread());
               }
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
                  fail("Cannot sleep for 100ms", e);
               }
               return true;
            })
            .endSequence();

      runScenario();
      assertThat(threads.size()).isEqualTo(CLIENT_THREADS);
   }

   @Override
   protected int threads() {
      return CLIENT_THREADS;
   }
}
