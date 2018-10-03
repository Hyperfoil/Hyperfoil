package io.sailrocket.core.builders;

public interface Rewritable<T> {
   void readFrom(T other);
}
