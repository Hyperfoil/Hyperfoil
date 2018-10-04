package io.sailrocket.core.impl;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.sailrocket.api.collection.ConcurrentPool;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConcurrentPoolImpl<T> implements ConcurrentPool<T> {
   private Logger log = LoggerFactory.getLogger(ConcurrentPoolImpl.class);
   private final Supplier<T> supplier;
   private BlockingQueue<T> primaryQueue;
   private final BlockingQueue<T> secondaryQueue = new LinkedBlockingQueue<>();

   public ConcurrentPoolImpl(Supplier<T> supplier) {
      this.supplier = supplier;
   }

   @Override
   public T acquire() {
      T object = primaryQueue.poll();
      if (object != null) {
         return object;
      }
      secondaryQueue.drainTo(primaryQueue, primaryQueue.remainingCapacity());
      object = primaryQueue.poll();
      if (object != null) {
         return object;
      }
      log.warn("Pool depleted, allocating new sessions!");
      return supplier.get();
   }

   @Override
   public void release(T object) {
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
         primaryQueue.add(supplier.get());
      }
   }

   @Override
   public void forEach(Consumer<T> consumer) {
      if (primaryQueue.remainingCapacity() < secondaryQueue.size()) {
         BlockingQueue<T> newQueue = new ArrayBlockingQueue<>(primaryQueue.size() + secondaryQueue.size());
         primaryQueue.drainTo(newQueue);
         primaryQueue = newQueue;
      }
      secondaryQueue.drainTo(primaryQueue, primaryQueue.remainingCapacity());
      assert secondaryQueue.isEmpty();
      primaryQueue.forEach(consumer);
   }
}
