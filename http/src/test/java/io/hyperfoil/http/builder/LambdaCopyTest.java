package io.hyperfoil.http.builder;

import org.junit.Test;

import io.hyperfoil.http.handlers.RangeStatusValidator;
import io.hyperfoil.http.steps.HttpRequestStep;

public class LambdaCopyTest {
   @Test
   public void test() {
      HttpRequestStep.Builder builder = new HttpRequestStep.Builder()
            .handler().status(new RangeStatusValidator(0, 666)).endHandler();
      builder.copy();
   }
}
