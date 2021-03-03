package io.hyperfoil.http.steps;

import io.hyperfoil.http.api.StatusHandler;

public final class HttpRequestStepUtil {

   public static StatusHandler[] statusHandlers(PrepareHttpRequestStep step) {
      return step.handler.statusHandlers.clone();
   }
}
