package io.hyperfoil.core.generators;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;

public interface StringGeneratorBuilder {
   SerializableFunction<Session, String> build();
}
