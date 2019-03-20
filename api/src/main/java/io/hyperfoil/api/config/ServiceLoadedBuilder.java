package io.hyperfoil.api.config;

import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Base for builders that are provided on classpath.
 */
public interface ServiceLoadedBuilder {
   /**
    * Creates the build object and passes it to the <code>buildTarget</code> provided by {@link Factory}.
    */
   void apply();

   /**
    * This interface should not be be implemented directly; more specific interface should extend it
    * and that would be the parameter of the {@link ServiceLoader#load(Class)} invocation.
    * @param <T>
    */
   interface Factory<T> {
      /**
       * @return Name unique across implementations of the concrete factory type. Does not need to be globally unique.
       */
      String name();

      /**
       * @return True if {@link #newBuilder(StepBuilder, Consumer, String)} can be called with non-null parameter.
       */
      boolean acceptsParam();

      /**
       * Constructs the builder, usually passing the build target as a constructor arg to the builder instance.
       *
       *
       * @param stepBuilder
       * @param buildTarget
       * @param param
       * @return
       * @throws IllegalArgumentException if the loader does not expect any parameter and it is not <code>null</code>.
       */
      ServiceLoadedBuilder newBuilder(StepBuilder stepBuilder, Consumer<T> buildTarget, String param);
   }

   abstract class Base<T> implements ServiceLoadedBuilder {
      private final Consumer<T> buildTarget;

      protected Base(Consumer<T> buildTarget) {
         this.buildTarget = buildTarget;
      }

      @Override
      public void apply() {
         buildTarget.accept(build());
      }

      protected abstract T build();
   }
}
