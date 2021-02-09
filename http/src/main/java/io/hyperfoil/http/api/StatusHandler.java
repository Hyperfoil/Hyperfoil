package io.hyperfoil.http.api;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;

public interface StatusHandler extends Serializable {
   void handleStatus(HttpRequest request, int status);

   interface Builder extends BuilderBase<Builder> {
      StatusHandler build();
   }
}
