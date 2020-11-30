package io.hyperfoil.core.generators;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;

@FunctionalInterface
public interface HttpMethodBuilder extends BuilderBase<HttpMethodBuilder> {
   SerializableFunction<Session, HttpMethod> build();

   class Provided implements SerializableFunction<Session, HttpMethod> {
      private final HttpMethod method;

      public Provided(HttpMethod method) {
         this.method = method;
      }

      @Override
      public HttpMethod apply(Session o) {
         return method;
      }
   }
}
