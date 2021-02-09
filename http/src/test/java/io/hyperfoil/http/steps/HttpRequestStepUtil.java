package io.hyperfoil.http.steps;

import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.api.StatusHandler;

public final class HttpRequestStepUtil {
   public static HttpResponseHandlers handlers(HttpRequestStep step) {
      return step.handler;
   }

   public static StatusHandler[] statusHandlers(HttpRequestStep step) {
      return step.handler.statusHandlers.clone();
   }
}
