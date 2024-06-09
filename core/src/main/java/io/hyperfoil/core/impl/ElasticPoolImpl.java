package io.hyperfoil.core.impl;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import io.hyperfoil.api.collection.ElasticPool;
import io.netty.util.internal.PlatformDependent;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.MpmcUnboundedXaddArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

public class ElasticPoolImpl<T> implements ElasticPool<T> {
   protected final LongAdder used = new LongAdder();
   private final Supplier<T> initSupplier;
   private final Supplier<T> depletionSupplier;
   protected volatile int minUsed;
   protected volatile int maxUsed;
   private Queue<T> primaryQueue;
   private final Queue<T> secondaryQueue = PlatformDependent.hasUnsafe() ?
           new MpmcUnboundedXaddArrayQueue<>(128) :
           new ConcurrentLinkedQueue<>();

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
        if (primaryQueue == null) {
            final int qCapacity = Math.max(2, capacity);
            primaryQueue = PlatformDependent.hasUnsafe() ?
                    new MpmcArrayQueue<>(qCapacity) :
                    new MpmcAtomicArrayQueue<>(qCapacity);
            for (int i = 0; i < capacity; i++) {
                primaryQueue.offer(initSupplier.get());
            }
            assert primaryQueue.size() == capacity;
            return;
        }
        final int currentCapacity = primaryQueue.size();
        if (currentCapacity >= capacity) {
            return;
        }
        var oldStorage = primaryQueue;
        primaryQueue = null;
        final int qCapacity = Math.max(2, capacity);
        primaryQueue = PlatformDependent.hasUnsafe() ?
                new MpmcArrayQueue<>(qCapacity) :
                new MpmcAtomicArrayQueue<>(qCapacity);
        for (int i = 0; i < currentCapacity; i++) {
            primaryQueue.offer(oldStorage.poll());
        }
        for (int i = currentCapacity; i < capacity; i++) {
            primaryQueue.offer(initSupplier.get());
        }
        assert primaryQueue.size() == capacity;
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
