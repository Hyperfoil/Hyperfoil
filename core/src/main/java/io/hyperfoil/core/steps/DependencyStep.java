package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class DependencyStep extends BaseStep {
   private static final Logger log = LoggerFactory.getLogger(Step.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Access[] dependencies;

   protected DependencyStep(SerializableSupplier<Sequence> sequence, Access[] dependencies) {
      super(sequence);
      this.dependencies = dependencies;
   }

   @Override
   public boolean invoke(Session session) {
      if (dependencies != null) {
         for (Access ref : dependencies) {
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
