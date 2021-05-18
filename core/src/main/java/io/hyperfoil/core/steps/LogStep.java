package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * This is a debugging step.
 */
public class LogStep implements Step {
   private static final Logger log = LogManager.getLogger(LogStep.class);

   private final String message;
   private final ReadAccess[] vars;

   public LogStep(String message, ReadAccess[] vars) {
      this.message = message;
      this.vars = vars;
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
            } else if (var.type() == VarType.OBJECT) {
               objects[i] = var.objectValue(session);
            } else if (var.type() == VarType.INTEGER) {
               objects[i] = var.intValue(session);
            } else {
               objects[i] = "<unknown type>";
            }
         }
         log.info(message, objects);
      }
      return true;
   }

   /**
    * Log a message and variable values.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("log")
   public static class Builder extends BaseStepBuilder<Builder> {
      String message;
      List<String> vars = new ArrayList<>();

      /**
       * Message format pattern. Use <code>{}</code> to mark the positions for variables in the logged message.
       *
       * @param message Message format pattern.
       * @return Self.
       */
      public Builder message(String message) {
         this.message = message;
         return this;
      }

      /**
       * List of variables to be logged.
       *
       * @return Builder.
       */
      public ListBuilder vars() {
         return vars::add;
      }

      // the ignored parameter removes this method from YAML-based configuration
      public Builder addVar(String var, Void ignored) {
         vars.add(var);
         return this;
      }

      @Override
      public List<Step> build() {
         if (message == null) {
            throw new BenchmarkDefinitionException("Missing message");
         }
         return Collections.singletonList(new LogStep(message, vars.stream().map(SessionFactory::readAccess).toArray(ReadAccess[]::new)));
      }
   }
}
