package io.sailrocket.api.config;

import java.util.function.Consumer;

public interface ServiceLoadedBuilder {
   void apply();

   interface Factory<T> {
      String name();
      ServiceLoadedBuilder newBuilder(Consumer<T> buildTarget, String param);
   }
}
