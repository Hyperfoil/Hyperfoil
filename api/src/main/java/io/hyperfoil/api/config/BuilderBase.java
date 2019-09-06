package io.hyperfoil.api.config;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Intended base for all builders that might need relocation when the step is copied over.
 */
public interface BuilderBase<S extends BuilderBase<S>> {
   default void prepareBuild() {}

   /**
    * Should be overridden if the <code>locator</code> parameter in {@link ServiceLoadedFactory#newBuilder(Locator, String)}
    * is used. If the locator is not used it is legal to return <code>this</code>.
    *
    * @param locator
    * @return Deep copy of this object.
    */
   @SuppressWarnings("unchecked")
   default S copy(Locator locator) {
      return (S) this;
   }

   static <T extends BuilderBase<T>> List<T> copy(Locator locator, Collection<? extends BuilderBase<T>> builders) {
      return builders.stream().map(b -> b.copy(locator)).collect(Collectors.toList());
   }
}
