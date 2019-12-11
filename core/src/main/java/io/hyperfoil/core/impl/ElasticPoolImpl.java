package io.hyperfoil.core.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import io.hyperfoil.api.collection.ElasticPool;

public class ElasticPoolImpl<T> implements ElasticPool<T> {
   private final Supplier<T> initSupplier;
   private final Supplier<T> depletionSupplier;
   private ArrayBlockingQueue<T> primaryQueue;
   private final BlockingQueue<T> secondaryQueue = new LinkedBlockingQueue<>();
   private final LongAdder used = new LongAdder();
   private volatile int minUsed = Integer.MAX_VALUE, maxUsed;

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

   private void incrementUsed() {
      used.increment();
      long currentlyUsed = used.longValue();
      if (currentlyUsed > maxUsed) {
         maxUsed = (int) currentlyUsed;
      }
   }

   @Override
   public void release(T object) {
      used.decrement();
      long currentlyUsed = used.longValue();
      if (currentlyUsed < minUsed) {
         minUsed = (int) currentlyUsed;
      }
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

   @Override
   public int minUsed() {
      return minUsed;
   }

   @Override
   public int maxUsed() {
      return maxUsed;
   }

   @Override
   public void resetStats() {
      int current = used.intValue();
      minUsed = current;
      maxUsed = current;
   }
}
