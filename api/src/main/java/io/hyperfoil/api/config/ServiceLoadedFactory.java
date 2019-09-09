package io.hyperfoil.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ServiceLoader;

/**
 * This interface should not be be implemented directly; more specific interface should extend it
 * and that would be the parameter of the {@link ServiceLoader#load(Class)} invocation.
 */
public interface ServiceLoadedFactory<B> {
   /**
    * @return Name unique across implementations of the concrete factory type. Does not need to be globally unique.
    */
   String name();

   /**
    * @return True if {@link #newBuilder(Locator, String)} can be called with non-null parameter.
    */
   boolean acceptsParam();

   /**
    * Constructs the builder, usually passing the build target as a constructor arg to the builder instance.
    *
    * @param locator Locator identifying the place for insertion.
    * @param param Custom parameter for inline definition.
    * @return Builder.
    * @throws IllegalArgumentException if the loader does not expect any parameter and it is not <code>null</code>.
    */
   B newBuilder(Locator locator, String param);

   /**
    * Mark this class with this annotation to include covariant factories.
    */
   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.TYPE)
   @interface Include {
      Class<? extends ServiceLoadedFactory<?>>[] value();
   }
}
