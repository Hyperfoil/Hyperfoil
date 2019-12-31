package io.hyperfoil.api.http;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.connection.HttpRequest;

public interface HeaderHandler extends Serializable {
   default void beforeHeaders(HttpRequest request) {
   }

   void handleHeader(HttpRequest request, CharSequence header, CharSequence value);

   default void afterHeaders(HttpRequest request) {
   }

   interface Builder extends BuilderBase<Builder> {
      HeaderHandler build();
   }
}
