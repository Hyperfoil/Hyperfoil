package io.hyperfoil.core.impl;

import java.util.function.Supplier;

import io.hyperfoil.api.collection.ElasticPool;

public class LockBasedElasticPoolTest extends ElasticPoolTest<Object> {

   @Override
   protected ElasticPool<Object> createPoolWith(final Supplier<Object> initSupplier, final Supplier<Object> depletionSupplier) {
      return new LockBasedElasticPool<>(initSupplier, depletionSupplier);
   }

   @Override
   protected Object createNewItem() {
      return new Object();
   }

}
