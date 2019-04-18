package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This is a debugging step.
 */
public class LogStep implements Step {
   private static final Logger log = LoggerFactory.getLogger(LogStep.class);

   private final String message;
   private final Access[] vars;

   public LogStep(String message, List<String> vars) {
      this.message = message;
      this.vars = vars.stream().map(SessionFactory::access).toArray(Access[]::new);
   }

   @Override
   public boolean invoke(Session session) {
      // Normally we wouldn't allocate objects but since this should be used for debugging...
      if (vars.length == 0) {
         log.info(message);
      } else {
         Object[] objects = new Object[vars.length];
         for (int i = 0; i < vars.length; ++i) {
            Session.Var var = vars[i].getVar(session);
            if (!var.isSet()) {
               objects[i] = "<not set>";
            } else if (var instanceof ObjectVar) {
               objects[i] = var.objectValue();
            } else if (var instanceof IntVar) {
               objects[i] = var.intValue();
            } else {
               objects[i] = "<unknown type>";
            }
         }
         log.info(message, objects);
      }
      return true;
   }

   public static class Builder extends BaseStepBuilder {
      String message;
      List<String> vars = new ArrayList<>();

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder message(String message) {
         this.message = message;
         return this;
      }

      public ListBuilder vars() {
         return vars::add;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (message == null) {
            throw new BenchmarkDefinitionException("Missing message");
         }
         return Collections.singletonList(new LogStep(message, vars));
      }
   }
}
