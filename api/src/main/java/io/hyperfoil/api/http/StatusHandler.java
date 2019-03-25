package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.connection.Request;

public interface StatusHandler extends Serializable {
   void handleStatus(Request request, int status);

   interface Builder {
      default void prepareBuild() {}
      StatusHandler build();
   }
   interface BuilderFactory extends ServiceLoadedFactory<StatusHandler.Builder> {}
}
