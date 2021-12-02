package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.core.session.SessionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * This is a debugging step.
 */
public class LogAction implements Action {
   private static final Logger log = LogManager.getLogger(LogAction.class);

   private final String message;
   private final ReadAccess[] vars;

   public LogAction(String message, ReadAccess[] vars) {
      this.message = message;
      this.vars = vars;
   }

   @Override
   public void run(Session session) {
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
   }

   /**
    * Log a message and variable values.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("log")
   public static class Builder<P> implements Action.Builder {
      private final P parent;
      String message;
      List<String> vars = new ArrayList<>();

      public Builder(P parent) {
         this.parent = parent;
      }

      public Builder() {
         this(null);
      }

      public P end() {
         return parent;
      }

      /**
       * Message format pattern. Use <code>{}</code> to mark the positions for variables in the logged message.
       *
       * @param message Message format pattern.
       * @return Self.
       */
      public Builder<P> message(String message) {
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
      public Builder<P> addVar(String var, Void ignored) {
         vars.add(var);
         return this;
      }

      @Override
      public LogAction build() {
         if (message == null) {
            throw new BenchmarkDefinitionException("Missing message");
         }
         return new LogAction(message, vars.stream().map(SessionFactory::readAccess).toArray(ReadAccess[]::new));
      }
   }
}
