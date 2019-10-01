package io.hyperfoil.core.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Concurrent data structure that returns elements randomly. This is not called 'pool' because we don't
 * expect to wipe out and overwrite the objects completely after {@link #fetch()}.
 * The structure is mostly non-blocking, with an exception when it needs to be resized (the blocking part
 * is quite short there as well, though).
 * Regular operations (not resizing) should not cause any allocations, too.
 */
public class RandomConcurrentSet<T> {
   private final int maxPutLookup;
   private final int fetchAttempts;
   private final ReadWriteLock resizeLock = new ReentrantReadWriteLock();

   private volatile AtomicReferenceArray<T> fetchArray;
   private volatile AtomicReferenceArray<T> putArray;
   private volatile int reserved = 0;

   public RandomConcurrentSet(int initialCapacity, int maxPutLookup, int fetchAttempts) {
      this.maxPutLookup = maxPutLookup;
      this.fetchAttempts = fetchAttempts;

      fetchArray = putArray = new AtomicReferenceArray<>(initialCapacity);
   }

   public RandomConcurrentSet(int initialCapacity) {
      this(initialCapacity, 16, 16);
   }

   /**
    * @return Random object from the set or null. This object is exclusively owned by the caller now until it is returned.
    * When null is returned the caller should implement some back-off strategy (such as wait in a way not blocking
    * the thread) and retry later.
    */
   public T fetch() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      for (; ; ) {
         AtomicReferenceArray<T> fetchArray = this.fetchArray;
         for (int i = 0; i < fetchAttempts; ++i) {
            int idx = random.nextInt(fetchArray.length());
            T element = fetchArray.get(idx);
            if (element != null && fetchArray.compareAndSet(idx, element, null)) {
               return element;
            }
         }
         if (fetchArray != this.fetchArray) {
            continue;
         }
         if (fetchArray != putArray) {
            Lock lock = resizeLock.readLock();
            lock.lock();
            try {
               // we can set putArray to fetchArray any time (when we find the fetchArray too sparse)
               // because the resizing thread is obliged to move all data from the previous array.
               this.fetchArray = putArray;
            } finally {
               lock.unlock();
            }
            continue;
         }
         return null;
      }
   }

   /**
    * Insert a new object or an object previously returned by {@link #fetch()} to the set.
    *
    * @param object Any object.
    */
   public void put(T object) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Lock readLock = resizeLock.readLock();
      for (; ; ) {
         // This read lock makes sure that we don't insert anything to array that's going away
         readLock.lock();
         boolean isLocked = true;
         try {
            AtomicReferenceArray<T> putArray = this.putArray;
            for (int i = 0; i < maxPutLookup; ++i) {
               int idx = random.nextInt(reserved, putArray.length());
               if (putArray.get(idx) == null && putArray.compareAndSet(idx, null, object)) {
                  return;
               }
            }
            readLock.unlock();

            Lock writeLock = resizeLock.writeLock();

            AtomicReferenceArray<T> fetchArray;
            writeLock.lock();
            try {
               if (putArray != this.putArray) {
                  // If the array has been resized by another thread just retry
                  isLocked = false;
                  continue;
               }
               // It is not possible that other thread would be still moving data to the new array since it does
               // not release read lock until it has moved everything.
               fetchArray = this.fetchArray;
               assert fetchArray == putArray;

               // We'll reserve space for elements from fetchArray; once we'll release the write lock the other threads
               // still won't write before this limit
               reserved = putArray.length() + 1;
               this.putArray = putArray = new AtomicReferenceArray<>(putArray.length() * 2);

               // this downgrades write lock to read lock
               readLock.lock();
            } finally {
               writeLock.unlock();
            }

            putArray.set(0, object);
            int writeIdx = 1;
            for (int i = 0; i < fetchArray.length(); ++i) {
               T element = fetchArray.get(i);
               if (element != null && fetchArray.compareAndSet(i, element, null)) {
                  for (; writeIdx < putArray.length(); ++writeIdx) {
                     if (putArray.compareAndSet(writeIdx, null, element)) {
                        break;
                     }
                  }
               }
            }
            // Now that we have copied all data from fetchArray other threads can insert data to any position
            this.fetchArray = putArray;
            reserved = 0;
            return;
         } finally {
            if (isLocked) {
               readLock.unlock();
            }
         }
      }
   }

   // debug only, not thread-safe!
   void readAll(Consumer<T> consumer) {
      for (int i = 0; i < putArray.length(); ++i) {
         T element = putArray.get(i);
         if (element != null) {
            consumer.accept(element);
         }
      }
   }
}
