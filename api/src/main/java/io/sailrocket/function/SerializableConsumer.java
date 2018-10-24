package io.sailrocket.function;

import java.io.Serializable;
import java.util.function.Consumer;

public interface SerializableConsumer<T> extends Serializable, Consumer<T> {
}
