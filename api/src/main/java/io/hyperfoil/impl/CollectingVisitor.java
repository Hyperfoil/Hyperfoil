package io.hyperfoil.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import io.hyperfoil.api.config.Visitor;

/**
 * Walks an object graph and offers every instance of {@code T} to {@link #process(Object)}.
 * <p>
 * Scalars (see {@link ReflectionAcceptor#isScalar(Object)}) and primitive arrays are leaves: they are neither
 * traversed nor tracked, so the work and memory of a visit do not scale with the size of payloads such as
 * request bodies or large data sets. Elements of primitive arrays are therefore never offered to
 * {@link #process(Object)}, even if {@code T} is a boxed type; the array itself is offered when it is an instance
 * of {@code T}.
 */
public abstract class CollectingVisitor<T> implements Visitor {

   private final Map<Object, Object> seen = new IdentityHashMap<>();
   private final Class<T> clazz;

   public CollectingVisitor(Class<T> clazz) {
      this.clazz = clazz;
   }

   public void visit(Object root) {
      visit(null, root, null);
   }

   @Override
   public boolean visit(String name, Object value, Type fieldType) {
      if (value == null) {
         return false;
      }
      Class<?> cls = value.getClass();
      boolean collected = clazz.isInstance(value);
      if (!collected) {
         // Leaves are rejected before they are recorded in 'seen', which would otherwise grow with every
         // scalar element of a large collection or array. The array check precedes isScalar() as arrays
         // are never scalar and isScalar() is comparatively expensive.
         if (cls.isArray()) {
            if (cls.getComponentType().isPrimitive()) {
               return false;
            }
         } else if (ReflectionAcceptor.isScalar(value)) {
            return false;
         }
      }
      if (seen.put(value, value) != null) {
         return false;
      } else if (collected) {
         if (process(clazz.cast(value))) {
            ReflectionAcceptor.accept(value, this);
         }
      } else if (value instanceof Collection) {
         ((Collection<?>) value).forEach(item -> visit(null, item, null));
      } else if (value instanceof Map) {
         ((Map<?, ?>) value).forEach((k, v) -> visit(null, v, null));
      } else if (cls.isArray()) {
         int length = Array.getLength(value);
         for (int i = 0; i < length; ++i) {
            visit(null, Array.get(value, i), null);
         }
      } else {
         ReflectionAcceptor.accept(value, this);
      }
      // the return value doesn't matter here
      return false;
   }

   /**
    * @return Number of non-leaf objects encountered so far; exposed for tests.
    */
   int trackedObjects() {
      return seen.size();
   }

   protected abstract boolean process(T value);
}
