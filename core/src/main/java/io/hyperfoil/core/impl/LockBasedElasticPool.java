package io.hyperfoil.core.impl;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import io.hyperfoil.api.collection.ElasticPool;

public class LockBasedElasticPool<T> implements ElasticPool<T> {
   protected final LongAdder used = new LongAdder();
   private final Supplier<T> initSupplier;
   private final Supplier<T> depletionSupplier;
   protected volatile int minUsed;
   protected volatile int maxUsed;
   private ArrayBlockingQueue<T> primaryQueue;
   private final BlockingQueue<T> secondaryQueue = new LinkedBlockingQueue<>();

   public LockBasedElasticPool(Supplier<T> initSupplier, Supplier<T> depletionSupplier) {
      this.initSupplier = initSupplier;
      this.depletionSupplier = depletionSupplier;
   }

   @Override
   public T acquire() {
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
      Objects.requireNonNull(primaryQueue);
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

   public void incrementUsed() {
      used.increment();
      long currentlyUsed = used.longValue();
      if (currentlyUsed > maxUsed) {
         maxUsed = (int) currentlyUsed;
      }
   }

   public void decrementUsed() {
      decrementUsed(1);
   }

   public void decrementUsed(int num) {
      used.add(-num);
      long currentlyUsed = used.longValue();
      if (currentlyUsed < minUsed) {
         minUsed = (int) currentlyUsed;
      }
      assert currentlyUsed >= 0;
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

   public int current() {
      return used.intValue();
   }
}
