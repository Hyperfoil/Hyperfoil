package io.hyperfoil.api.collection;

/**
 * Pool that can create further elements when depleted.
 *
 * @param <T> The type of elements in this pool.
 */
public interface ElasticPool<T> {
   /**
    * This can be called by single thread only.
    *
    * @return pooled or new object.
    */
   T acquire();

   /**
    * Can be called by any thread.
    *
    * @param object Returned object.
    */
   void release(T object);

   void reserve(int capacity);

   int minUsed();

   int maxUsed();

   void resetStats();
}
