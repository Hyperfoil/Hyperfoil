package io.hyperfoil.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import io.hyperfoil.api.config.Visitor;

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
      } else if (seen.put(value, value) != null) {
         return false;
      } else if (clazz.isInstance(value)) {
         if (process(clazz.cast(value))) {
            ReflectionAcceptor.accept(value, this);
         }
      } else if (value instanceof Collection) {
         ((Collection<?>) value).forEach(item -> visit(null, item, null));
      } else if (value instanceof Map) {
         ((Map<?, ?>) value).forEach((k, v) -> visit(null, v, null));
      } else if (ReflectionAcceptor.isScalar(value)) {
         return false;
      } else if (value.getClass().isArray()) {
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

   protected abstract boolean process(T value);
}
