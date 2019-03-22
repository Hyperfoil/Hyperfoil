package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.connection.Request;

public interface StatusExtractor extends Serializable {
   void setStatus(Request request, int status);

   interface Builder {
      default void prepareBuild() {}
      StatusExtractor build();
   }
   interface BuilderFactory extends ServiceLoadedFactory<StatusExtractor.Builder> {}
}
