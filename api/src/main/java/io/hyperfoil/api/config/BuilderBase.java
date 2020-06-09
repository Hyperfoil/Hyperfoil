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
    * @return Deep copy of this object.
    */
   @SuppressWarnings("unchecked")
   default S copy() {
      return (S) this;
   }

   static <T extends BuilderBase<T>> List<T> copy(Collection<T> builders) {
      return builders.stream().map(b -> b.copy()).collect(Collectors.toList());
   }
}
