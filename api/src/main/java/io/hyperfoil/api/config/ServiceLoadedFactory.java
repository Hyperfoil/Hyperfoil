package io.hyperfoil.api.config;

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
    * @return True if {@link #newBuilder(StepBuilder, String)} can be called with non-null parameter.
    */
   boolean acceptsParam();

   /**
    * Constructs the builder, usually passing the build target as a constructor arg to the builder instance.
    *
    *
    * @param stepBuilder
    * @param param
    * @return
    * @throws IllegalArgumentException if the loader does not expect any parameter and it is not <code>null</code>.
    */
   B newBuilder(StepBuilder stepBuilder, String param);
}
