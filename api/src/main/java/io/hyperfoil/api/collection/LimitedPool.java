package io.hyperfoil.api.collection;

import java.util.Arrays;
import java.util.function.Supplier;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Fixed-size pool that can be accessed by single thread only.
 */
public class LimitedPool<T> {
   private static final Logger log = LoggerFactory.getLogger(LimitedPool.class);

   private Object[] elements;
   private int mask;
   private int index;

   public LimitedPool(int capacity, Supplier<T> init) {
      mask = (1 << 32 - Integer.numberOfLeadingZeros(capacity - 1)) - 1;
      elements = new Object[mask + 1];
      for (int i = 0; i < capacity; ++i) {
         elements[i] = init.get();
      }
   }

   public LimitedPool(T[] array) {
      mask = (1 << 32 - Integer.numberOfLeadingZeros(array.length - 1)) - 1;
      elements = new Object[mask + 1];
      System.arraycopy(array, 0, elements, 0, array.length);
   }

   public void reset(Object[] array) {
      if (array.length > elements.length) {
         throw new IllegalArgumentException();
      }
      System.arraycopy(array, 0, elements, 0, array.length);
      Arrays.fill(elements, array.length, elements.length, null);
   }

   public T acquire() {
      int i = (index + 1) & mask;
      while (i != index && elements[i] == null) {
         i = ((i + 1) & mask);
      }
      if (elements[i] == null) {
         return null;
      } else {
         index = i;
         @SuppressWarnings("unchecked")
         T object = (T) elements[i];
         elements[i] = null;
         return object;
      }
   }

   public void release(T object) {
      int i = index;
      int stop = (index + mask) & mask;
      while (i != stop && elements[i & mask] != null) ++i;
      if (elements[i] == null) {
         index = (i + mask) & mask;
         elements[i] = object;
      } else {
         // This should not happen...
         for (i = 0; i < elements.length; ++i) {
            if (elements[i] == object) {
               log.error("{} already returned to pool!", object);
               return;
            }
         }
         throw new IllegalStateException("Pool should not be full!");
      }
   }

   public boolean isFull() {
      for (Object o : elements) {
         if (o == null) return false;
      }
      return true;
   }

   public boolean isDepleted() {
      for (Object o : elements) {
         if (o != null) return false;
      }
      return true;
   }
}
