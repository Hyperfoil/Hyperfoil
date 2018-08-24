package io.sailrocket.core.steps;

import io.sailrocket.api.Session;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.session.SimpleVarReference;

public class AwaitVarStep extends BaseStep implements ResourceUtilizer {
   private final String var;

   public AwaitVarStep(String var) {
      this.var = var;
      addDependency(new SimpleVarReference(var));
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
