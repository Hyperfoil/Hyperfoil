package io.hyperfoil.core.steps.data;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class ReadAgentDataAction implements Action {
   private final String name;
   private final ObjectAccess toVar;

   public ReadAgentDataAction(String name, ObjectAccess toVar) {
      this.name = name;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      session.agentData().pull(session, name, toVar);
   }

   /**
    * Reads data from agent-wide scope into session variable. The data must be published in a phase that has terminated
    * before this phase starts: usually this is achieved using the <code>startAfterStrict</code> property on the phase.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("readAgentData")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String name;
      private String toVar;

      /**
       * @param param Both the identifier and destination session variable.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return name(param).toVar(param);
      }

      /**
       * Unique identifier for the data.
       *
       * @param name Identifier.
       * @return Self.
       */
      public Builder name(String name) {
         this.name = name;
         return this;
      }

      /**
       * Destination session variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public ReadAgentDataAction build() {
         if (name == null || name.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid key: " + name);
         }
         return new ReadAgentDataAction(name, SessionFactory.objectAccess(toVar));
      }
   }
}
