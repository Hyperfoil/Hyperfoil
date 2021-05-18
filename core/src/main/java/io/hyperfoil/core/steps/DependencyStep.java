package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public abstract class DependencyStep implements Step {
   private static final Logger log = LogManager.getLogger(Step.class);
   private static final boolean trace = log.isTraceEnabled();

   private final ReadAccess[] dependencies;

   protected DependencyStep(ReadAccess[] dependencies) {
      this.dependencies = dependencies;
   }

   @Override
   public boolean invoke(Session session) {
      if (dependencies != null) {
         for (ReadAccess ref : dependencies) {
            if (!ref.isSet(session)) {
               if (trace) {
                  log.trace("Sequence is blocked by missing var reference {}", ref);
               }
               return false;
            }
         }
      }
      return true;
   }
}
