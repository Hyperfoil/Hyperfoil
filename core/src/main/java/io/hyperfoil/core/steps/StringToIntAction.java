package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class StringToIntAction implements Action {
   private static final Logger log = LogManager.getLogger(StringToIntAction.class);

   private final ReadAccess fromVar;
   private final IntAccess toVar;

   public StringToIntAction(ReadAccess fromVar, IntAccess toVar) {
      this.fromVar = fromVar;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      Object value = fromVar.getObject(session);
      if (value == null) {
         log.error("#{} Cannot convert {} == null to integer", session.uniqueId(), fromVar);
      } else {
         try {
            int intValue = Integer.parseInt(value.toString());
            toVar.setInt(session, intValue);
         } catch (NumberFormatException e) {
            log.error("#{} Cannot convert {} to integer", session.uniqueId(), value);
         }
      }
   }

   @MetaInfServices(Action.Builder.class)
   @Name("stringToInt")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      String fromVar;
      String toVar;

      /**
       * Source variable name.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Target variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * @param param Use `fromVar -&gt; toVar`
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int index = param.indexOf("->");
         if (index < 0) {
            throw new BenchmarkDefinitionException("Wrong format: use 'fromVar -> toVar'");
         }
         fromVar = param.substring(0, index).trim();
         toVar = param.substring(index + 2).trim();
         if (fromVar.isEmpty() || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("Wrong format: use 'fromVar -> toVar'");
         }
         return this;
      }

      @Override
      public Action build() {
         if (fromVar == null || toVar == null) {
            throw new BenchmarkDefinitionException("Must set both `fromVar` and `toVar`.");
         } else if (fromVar.equals(toVar)) {
            throw new BenchmarkDefinitionException("Variable type is set statically; cannot use the same variable for both `fromVar` and `toVar`.");
         } else {
            return new StringToIntAction(SessionFactory.readAccess(fromVar), SessionFactory.intAccess(toVar));
         }
      }
   }
}
