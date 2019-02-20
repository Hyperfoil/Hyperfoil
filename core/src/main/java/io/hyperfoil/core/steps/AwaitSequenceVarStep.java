package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.session.SequenceScopedVarReference;

public class AwaitSequenceVarStep extends DependencyStep implements ResourceUtilizer {
   private final String var;

   public AwaitSequenceVarStep(String var) {
      super(null, new VarReference[] { new SequenceScopedVarReference(var)});
      this.var = var;
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }
}
