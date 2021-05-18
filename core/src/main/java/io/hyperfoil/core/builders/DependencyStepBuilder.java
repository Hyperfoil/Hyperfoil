package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.core.session.SessionFactory;

public abstract class DependencyStepBuilder<S extends DependencyStepBuilder<S>> extends BaseStepBuilder<S> {
   private Collection<String> dependencies = new ArrayList<>();

   /**
    * This step is blocked if this variable does not have set value (none by default).
    *
    * @param var Variable name.
    * @return Self.
    */
   @SuppressWarnings("unchecked")
   public S dependency(String var) {
      if (var != null) {
         dependencies.add(var);
      }
      return (S) this;
   }

   protected ReadAccess[] dependencies() {
      return dependencies.stream().map(SessionFactory::readAccess).toArray(ReadAccess[]::new);
   }
}
