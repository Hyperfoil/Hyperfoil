package io.sailrocket.api.collection;

import java.util.function.Consumer;

public interface ConcurrentPool<T> {
   /**
    * This will be always called by single thread only.
    */
   T acquire();

   /**
    * Can be called by any thread.
    */
   void release(T object);

   void reserve(int capacity);

   void forEach(Consumer<T> consumer);
}
