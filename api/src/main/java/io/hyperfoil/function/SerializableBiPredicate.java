package io.hyperfoil.function;

import java.io.Serializable;
import java.util.function.BiPredicate;

public interface SerializableBiPredicate<A, B> extends BiPredicate<A, B>, Serializable {
   @Override
   default SerializableBiPredicate<A, B> negate() {
      return (a, b) -> !test(a, b);
   }
}
