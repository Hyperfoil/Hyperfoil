package io.hyperfoil.core.builders;

public interface Rewritable<T> {
   void readFrom(T other);
}
