package io.hyperfoil.function;

import java.io.Serializable;
import java.util.function.BiPredicate;

public interface SerializableBiPredicate<A, B> extends BiPredicate<A, B>, Serializable {
}
