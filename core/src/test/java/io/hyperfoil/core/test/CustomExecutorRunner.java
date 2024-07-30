package io.hyperfoil.core.test;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

public class CustomExecutorRunner implements BeforeEachCallback, InvocationInterceptor {

   public static ExecutorService TEST_EVENT_EXECUTOR;

   // @Override
   // protected Statement methodBlock(final FrameworkMethod method) {
   //    Objects.requireNonNull(TEST_EVENT_EXECUTOR, "Executor is not set");
   //    final Statement statement = super.methodBlock(method);
   //    return new Statement() {
   //       @Override
   //       public void evaluate() throws Throwable {
   //          Future<?> future = TEST_EVENT_EXECUTOR.submit(() -> {
   //             try {
   //                statement.evaluate();
   //             } catch (Throwable throwable) {
   //                throw new RuntimeException(throwable);
   //             }
   //          });
   //          future.get(); // wait for the test to complete
   //       }
   //    };
   // }

   @Override
   public void beforeEach(ExtensionContext extensionContext) throws Exception {
      Objects.requireNonNull(TEST_EVENT_EXECUTOR, "Executor is not set");
   }

   @Override
   public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
         ExtensionContext extensionContext) throws Throwable {
      Future<?> future = TEST_EVENT_EXECUTOR.submit(() -> {
         try {
            invocation.proceed();
         } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
         }
      });
      future.get(); // wait for the test to complete
   }

   @Override
   public void interceptAfterAllMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
         ExtensionContext extensionContext) throws Throwable {
      var executor = TEST_EVENT_EXECUTOR;
      if (executor != null) {
         executor.shutdown();
      }
   }
}
