package io.hyperfoil.http.builder;

import org.junit.jupiter.api.Test;

import io.hyperfoil.http.handlers.RangeStatusValidator;
import io.hyperfoil.http.steps.HttpRequestStepBuilder;

public class LambdaCopyTest {
   @Test
   public void test() {
      HttpRequestStepBuilder builder = new HttpRequestStepBuilder()
            .handler().status(new RangeStatusValidator(0, 666)).endHandler();
      builder.copy(null);
   }
}
