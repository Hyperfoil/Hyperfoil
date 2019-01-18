package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.session.SimpleVarReference;
import io.hyperfoil.function.SerializableSupplier;

public class AwaitVarStep extends DependencyStep implements ResourceUtilizer {
   private final String var;

   public AwaitVarStep(SerializableSupplier<Sequence> sequence, String var) {
      super(sequence, new VarReference[] { new SimpleVarReference(var) });
      this.var = var;
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }
}
