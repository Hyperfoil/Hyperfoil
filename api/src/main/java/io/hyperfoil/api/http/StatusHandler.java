package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.connection.Request;

public interface StatusHandler extends Serializable {
   void handleStatus(Request request, int status);

   interface Builder extends BuilderBase<Builder> {
      StatusHandler build();
   }
}
