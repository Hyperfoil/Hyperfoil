package io.hyperfoil.api.config;

public interface Rewritable<T> {
   void readFrom(T other);
}
