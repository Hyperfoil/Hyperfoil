package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializablePredicate;

public interface Condition extends SerializablePredicate<Session> {
   interface Builder {
      SerializablePredicate<Session> build();
   }
}
