package io.hyperfoil.core.builders;

/**
 * Fill-in the builder provided in {@link #builder()} and then call {@link #complete()}.
 */
public final class ServiceLoadedContract {
   private final Object builder;
   private final Runnable completion;

   public ServiceLoadedContract(Object builder, Runnable completion) {
      this.builder = builder;
      this.completion = completion;
   }

   public Object builder() {
      return builder;
   }

   public void complete() {
      completion.run();
   }
}
