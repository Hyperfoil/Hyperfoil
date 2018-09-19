package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.sailrocket.api.session.VarReference;

public abstract class DependencyStepBuilder extends BaseStepBuilder {
   private Collection<VarReference> dependencies = new ArrayList<>();

   protected DependencyStepBuilder(BaseSequenceBuilder parent) {
      super(parent);
   }

   public DependencyStepBuilder dependency(VarReference varReference) {
      dependencies.add(varReference);
      return this;
   }

   protected VarReference[] dependencies() {
      return dependencies.toArray(new VarReference[0]);
   }
}
