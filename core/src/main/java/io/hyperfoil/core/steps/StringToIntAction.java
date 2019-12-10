package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StringToIntAction implements Action, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(StringToIntAction.class);

   private final Access fromVar;
   private final Access toVar;

   public StringToIntAction(Access fromVar, Access toVar) {
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

   @Override
   public void reserve(Session session) {
      toVar.declareInt(session);
   }

   @MetaInfServices(Action.Builder.class)
   @Name("stringToInt")
   public static class Builder implements Action.Builder {
      String fromVar;
      String toVar;

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public Action build() {
         if (fromVar == null || toVar == null) {
            throw new BenchmarkDefinitionException("Must set both `fromVar` and `toVar`.");
         } else if (fromVar.equals(toVar)) {
            throw new BenchmarkDefinitionException("Variable type is set statically; cannot use the same variable for both `fromVar` and `toVar`.");
         } else {
            return new StringToIntAction(SessionFactory.access(fromVar), SessionFactory.access(toVar));
         }
      }
   }
}
