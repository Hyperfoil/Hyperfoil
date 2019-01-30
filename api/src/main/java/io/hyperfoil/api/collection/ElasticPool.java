package io.hyperfoil.api.collection;

import java.util.function.Consumer;

/**
 * Pool that can create further elements when depleted.
 * @param <T>
 */
public interface ElasticPool<T> {
   /**
    * This can be called by single thread only.
    *
    * Never returns null.
    */
   T acquire();

   /**
    * Can be called by any thread.
    */
   void release(T object);

   void reserve(int capacity);

   void forEach(Consumer<T> consumer);
}
