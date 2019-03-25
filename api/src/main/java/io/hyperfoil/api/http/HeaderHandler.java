package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.connection.Request;

public interface HeaderHandler extends Serializable {
   default void beforeHeaders(Request request) {
   }

   void handleHeader(Request request, String header, String value);

   default void afterHeaders(Request request) {
   }

   interface Builder {
      default void prepareBuild() {}
      HeaderHandler build();
   }
   interface BuilderFactory extends ServiceLoadedFactory<HeaderHandler.Builder> {}
}
