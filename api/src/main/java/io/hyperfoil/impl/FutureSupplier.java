package io.hyperfoil.impl;

import io.hyperfoil.function.SerializableSupplier;

public class FutureSupplier<T> implements SerializableSupplier<T> {
   private T object;

   public void set(T object) {
      assert this.object == null;
      assert object != null;
      this.object = object;
   }

   @Override
   public T get() {
      return object;
   }
}
