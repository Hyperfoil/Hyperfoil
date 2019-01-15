package io.hyperfoil.function;

import java.io.Serializable;
import java.util.function.BiConsumer;

public interface SerializableBiConsumer<A, B> extends Serializable, BiConsumer<A, B> {
}
