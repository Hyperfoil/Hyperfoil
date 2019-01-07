package io.sailrocket.function;

import java.io.Serializable;
import java.util.function.BiFunction;

public interface SerializableBiFunction<T1, T2, R> extends Serializable, BiFunction<T1, T2, R> {
}
