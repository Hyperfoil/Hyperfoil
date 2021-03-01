package io.hyperfoil.core.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.core.util.Watermarks;

public class ElasticPoolImpl<T> extends Watermarks implements ElasticPool<T> {
   private final Supplier<T> initSupplier;
   private final Supplier<T> depletionSupplier;
   private ArrayBlockingQueue<T> primaryQueue;
   private final BlockingQueue<T> secondaryQueue = new LinkedBlockingQueue<>();

   public ElasticPoolImpl(Supplier<T> initSupplier, Supplier<T> depletionSupplier) {
      this.initSupplier = initSupplier;
      this.depletionSupplier = depletionSupplier;
   }

   @Override
   public T acquire() {
      // TODO: there's quite some contention on the primary queue.
      //  Try to use queue per executor with work-stealing pattern.
      T object = primaryQueue.poll();
      if (object != null) {
         incrementUsed();
         return object;
      }
      object = secondaryQueue.poll();
      if (object != null) {
         incrementUsed();
         return object;
      }
      object = depletionSupplier.get();
      if (object != null) {
         incrementUsed();
      }
      return object;
   }

   @Override
   public void release(T object) {
      decrementUsed();
      if (primaryQueue.offer(object)) {
         return;
      }
      secondaryQueue.add(object);
   }

   @Override
   public void reserve(int capacity) {
      if (primaryQueue == null || primaryQueue.size() < capacity) {
         primaryQueue = new ArrayBlockingQueue<>(capacity);
      }
      while (primaryQueue.size() < capacity) {
         primaryQueue.add(initSupplier.get());
      }
   }
}
