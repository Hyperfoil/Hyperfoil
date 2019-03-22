package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.connection.Request;

public interface HeaderExtractor extends Serializable {
   default void beforeHeaders(Request request) {
   }

   void extractHeader(Request request, String header, String value);

   default void afterHeaders(Request request) {
   }

   interface Builder {
      default void prepareBuild() {}
      HeaderExtractor build();
   }
   interface BuilderFactory extends ServiceLoadedFactory<HeaderExtractor.Builder> {}
}
