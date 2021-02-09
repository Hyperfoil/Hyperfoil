package io.hyperfoil.core.data;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Supplier;

import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.session.Session;

public class LimitedPoolResource<T> extends LimitedPool<T> implements Session.Resource {
   protected final Object[] originalObjects;

   private LimitedPoolResource(T[] array) {
      super(array);
      originalObjects = array;
   }

   protected LimitedPoolResource(int capacity, Class<T> clz, Supplier<T> init) {
      this(createArray(capacity, clz, init));
   }

   public static <T> LimitedPoolResource<T> create(int capacity, Class<T> clz, Supplier<T> init) {
      return new LimitedPoolResource<>(createArray(capacity, clz, init));
   }

   private static <T> T[] createArray(int capacity, Class<T> clz, Supplier<T> init) {
      @SuppressWarnings("unchecked")
      T[] array = (T[]) Array.newInstance(clz, capacity);
      Arrays.setAll(array, i -> init.get());
      return array;
   }

   @Override
   public void onSessionReset(Session session) {
      reset(originalObjects);
   }

   public static class Key<T> implements Session.ResourceKey<LimitedPoolResource<T>> {}
}
