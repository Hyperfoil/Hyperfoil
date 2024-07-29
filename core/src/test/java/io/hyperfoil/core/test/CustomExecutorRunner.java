package io.hyperfoil.core.test;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

public class CustomExecutorRunner extends BlockJUnit4ClassRunner {

   public static ExecutorService TEST_EVENT_EXECUTOR;

   public CustomExecutorRunner(Class<?> klass) throws InitializationError {
      super(klass);
   }

   @Override
   protected Statement methodBlock(final FrameworkMethod method) {
      Objects.requireNonNull(TEST_EVENT_EXECUTOR, "Executor is not set");
      final Statement statement = super.methodBlock(method);
      return new Statement() {
         @Override
         public void evaluate() throws Throwable {
            Future<?> future = TEST_EVENT_EXECUTOR.submit(() -> {
               try {
                  statement.evaluate();
               } catch (Throwable throwable) {
                  throw new RuntimeException(throwable);
               }
            });
            future.get(); // wait for the test to complete
         }
      };
   }

   @Override
   public void run(RunNotifier notifier) {
      try {
         super.run(notifier);
      } finally {
         var executor = TEST_EVENT_EXECUTOR;
         if (executor != null) {
            executor.shutdown();
         }
      }
   }
}
