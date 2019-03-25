package io.hyperfoil.core.steps;

import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.StatusHandler;

public final class HttpRequestStepUtil {
   public static HttpResponseHandlers handlers(HttpRequestStep step) {
      return step.handler;
   }

   public static StatusHandler[] statusHandlers(HttpRequestStep step) {
      return step.handler.statusHandlers.clone();
   }
}
