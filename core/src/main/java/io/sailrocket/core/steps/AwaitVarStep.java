package io.sailrocket.core.steps;

import io.sailrocket.api.Session;
import io.sailrocket.api.VarReference;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.session.SimpleVarReference;

public class AwaitVarStep extends DependencyStep implements ResourceUtilizer {
   private final String var;

   public AwaitVarStep(String var) {
      super(new VarReference[] { new SimpleVarReference(var) });
      this.var = var;
   }

   @Override
   public void invoke(Session session) {
      // noop
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }
}
