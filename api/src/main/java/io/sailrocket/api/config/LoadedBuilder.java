package io.sailrocket.api.config;

import java.util.function.Consumer;

public interface LoadedBuilder {
   void apply();

   interface Factory<T> {
      String name();
      LoadedBuilder newBuilder(Consumer<T> buildTarget);
   }
}
