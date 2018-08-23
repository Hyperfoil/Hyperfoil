package io.sailrocket.core.machine;

import java.util.function.Supplier;

class Pool<T> {
   private Object[] elements;
   private int mask;
   private int index;

   Pool(int capacity, Supplier<T> init) {
      mask = (1 << 32 - Integer.numberOfLeadingZeros(capacity - 1)) - 1;
      elements = new Object[mask + 1];
      for (int i = 0; i < elements.length; ++i) {
         elements[i] = init.get();
      }
   }

   public T acquire() {
      int i = index + 1;
      while (i != index && elements[i & mask] == null) ++i;
      if (elements[i & mask] == null) {
         return null;
      } else {
         index = i;
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
         throw new IllegalStateException("Pool should not be full!");
      }
   }

   public void checkFull() {
      for (Object o : elements) {
         assert o != null;
      }
   }
}
