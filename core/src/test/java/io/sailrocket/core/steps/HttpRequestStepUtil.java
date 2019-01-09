package io.sailrocket.core.steps;

import io.sailrocket.api.http.HttpResponseHandlers;
import io.sailrocket.api.http.StatusValidator;

public final class HttpRequestStepUtil {
   public static HttpResponseHandlers handlers(HttpRequestStep step) {
      return step.handler;
   }

   public static StatusValidator[] statusValidators(HttpRequestStep step) {
      return step.handler.statusValidators.clone();
   }
}
