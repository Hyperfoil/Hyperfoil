package io.hyperfoil.core.generators;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;

@FunctionalInterface
public interface HttpMethodBuilder extends BuilderBase<HttpMethodBuilder> {
   SerializableFunction<Session, HttpMethod> build();
}
