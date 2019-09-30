package io.hyperfoil.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

/**
 * Mark this (abstract builder) interface with this annotation to include covariant builders.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IncludeBuilders {
   Conversion[] value();

   @interface Conversion {
      /**
       * @return Builder class that should be loaded in addition to the one the {@link IncludeBuilders}
       * annotation is attached to.
       */
      Class<?> from();

      /**
       * @return Class implementing function that adapts builder loaded through {@link #from()}
       * to builder of type where {@link IncludeBuilders} is attached.
       */
      Class<? extends Function<?, ?>> adapter();
   }
}
