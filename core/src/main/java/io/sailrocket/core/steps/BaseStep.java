package io.sailrocket.core.steps;

import java.util.Arrays;

import io.sailrocket.api.Step;
import io.sailrocket.api.VarReference;
import io.sailrocket.api.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class BaseStep implements Step {
   private static final Logger log = LoggerFactory.getLogger(Step.class);
   private static final boolean trace = log.isTraceEnabled();

   private VarReference[] varReferences;

   @Override
   public boolean prepare(Session session) {
      if (varReferences != null) {
         for (VarReference ref : varReferences) {
            if (!ref.isSet(session)) {
               log.trace("Sequence is blocked by missing var reference {}", ref);
               return false;
            }
         }
      }
      return true;
   }

   public void addDependency(VarReference ref) {
      if (varReferences == null) {
         varReferences = new VarReference[] { ref };
      } else {
         varReferences = Arrays.copyOf(varReferences, varReferences.length + 1);
         varReferences[varReferences.length - 1] = ref;
      }
   }

}
