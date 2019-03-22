package io.hyperfoil.api.config;

import java.util.function.Consumer;

/**
 * Fill-in the builder provided in {@link #builder()} and then call {@link #complete()}.
 */
public final class ServiceLoadedContract<B> {
   private final B builder;
   private final Consumer<B> consumer;

   public ServiceLoadedContract(B builder, Consumer<B> consumer) {
      this.builder = builder;
      this.consumer = consumer;
   }

   public B builder() {
      return builder;
   }

   public void complete() {
      consumer.accept(builder);
   }
}
