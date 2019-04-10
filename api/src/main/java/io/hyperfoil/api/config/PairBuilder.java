package io.hyperfoil.api.config;

import java.util.function.BiConsumer;

public interface PairBuilder<V> extends BiConsumer<String, V> {
   Class<V> valueType();

   abstract class OfString implements PairBuilder<String> {
      @Override
      public Class<String> valueType() {
         return String.class;
      }
   }
}
