package io.hyperfoil.core.builders;

import io.hyperfoil.function.SerializableSupplier;

class FutureSupplier<T> implements SerializableSupplier<T> {
   private T object;

   void set(T object) {
      assert this.object == null;
      assert object != null;
      this.object = object;
   }

   @Override
   public T get() {
      return object;
   }
}
