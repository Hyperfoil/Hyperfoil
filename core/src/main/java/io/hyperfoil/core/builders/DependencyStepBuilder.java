package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;

public abstract class DependencyStepBuilder<S extends DependencyStepBuilder<S>> extends BaseStepBuilder {
   private Collection<Access> dependencies = new ArrayList<>();

   protected DependencyStepBuilder(BaseSequenceBuilder parent) {
      super(parent);
   }

   /**
    * This step is blocked if this variable does not have set value (none by default).
    */
   @SuppressWarnings("unchecked")
   public S dependency(String var) {
      if (var != null) {
         dependencies.add(SessionFactory.access(var));
      }
      return (S) this;
   }

   protected Access[] dependencies() {
      return dependencies.toArray(new Access[0]);
   }
}
