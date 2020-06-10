package io.hyperfoil.core.builder;

import org.junit.Test;

import io.hyperfoil.core.handlers.RangeStatusValidator;
import io.hyperfoil.core.steps.HttpRequestStep;

public class LambdaCopyTest {
   @Test
   public void test() {
      HttpRequestStep.Builder builder = new HttpRequestStep.Builder()
            .handler().status(new RangeStatusValidator(0, 666)).endHandler();
      builder.copy();
   }
}
