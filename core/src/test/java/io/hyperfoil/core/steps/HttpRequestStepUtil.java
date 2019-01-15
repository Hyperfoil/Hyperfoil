package io.hyperfoil.core.steps;

import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.StatusValidator;

public final class HttpRequestStepUtil {
   public static HttpResponseHandlers handlers(HttpRequestStep step) {
      return step.handler;
   }

   public static StatusValidator[] statusValidators(HttpRequestStep step) {
      return step.handler.statusValidators.clone();
   }
}
