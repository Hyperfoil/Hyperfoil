package io.hyperfoil.schema;

import java.lang.reflect.ParameterizedType;

class BaseGenerator {
   static Class<?> getRawClass(java.lang.reflect.Type type) {
      if (type instanceof Class) {
         return (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
         return (Class<?>) ((ParameterizedType) type).getRawType();
      } else {
         throw new IllegalStateException("Cannot analyze type " + type);
      }
   }
}
