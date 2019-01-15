package io.hyperfoil.api.config;

import java.util.function.BiConsumer;

public interface PairBuilder<V> extends BiConsumer<String, V> {
   Class<V> valueType();

   abstract class String implements PairBuilder<java.lang.String> {
      @Override
      public Class<java.lang.String> valueType() {
         return java.lang.String.class;
      }
   }
}
