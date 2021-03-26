package io.hyperfoil.api.collection;

import java.util.Arrays;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Fixed-size pool that can be accessed by single thread only.
 */
public class LimitedPool<T> {
   private static final Logger log = LogManager.getLogger(LimitedPool.class);

   private final Object[] elements;
   private final int size;
   private final int mask;
   private int index;

   public LimitedPool(int capacity, Supplier<T> init) {
      mask = (1 << 32 - Integer.numberOfLeadingZeros(capacity - 1)) - 1;
      elements = new Object[mask + 1];
      size = capacity;
      for (int i = 0; i < capacity; ++i) {
         elements[i] = init.get();
      }
   }

   public LimitedPool(T[] array) {
      mask = (1 << 32 - Integer.numberOfLeadingZeros(array.length - 1)) - 1;
      elements = new Object[mask + 1];
      size = array.length;
      System.arraycopy(array, 0, elements, 0, array.length);
   }

   public void reset(Object[] array) {
      if (array.length != size) {
         throw new IllegalArgumentException("Pool should be initialized with " + size + " objects (actual: " + array.length + ")");
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
      int currentSize = 0;
      for (Object o : elements) {
         if (o != null) {
            currentSize++;
         }
      }
      return currentSize == size;
   }

   public boolean isDepleted() {
      for (Object o : elements) {
         if (o != null) return false;
      }
      return true;
   }
}
