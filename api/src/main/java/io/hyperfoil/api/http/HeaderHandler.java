package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.function.SerializableSupplier;

public interface HeaderHandler extends Serializable {
   default void beforeHeaders(HttpRequest request) {
   }

   void handleHeader(HttpRequest request, CharSequence header, CharSequence value);

   default void afterHeaders(HttpRequest request) {
   }

   interface Builder extends BuilderBase<Builder> {
      HeaderHandler build(SerializableSupplier<? extends Step> step);
   }
   interface BuilderFactory extends ServiceLoadedFactory<HeaderHandler.Builder> {}
}
