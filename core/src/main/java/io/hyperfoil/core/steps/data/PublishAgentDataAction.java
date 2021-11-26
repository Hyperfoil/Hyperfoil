package io.hyperfoil.core.steps.data;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class PublishAgentDataAction implements Action {
   private final String name;
   private final ReadAccess fromVar;

   public PublishAgentDataAction(String name, ReadAccess fromVar) {
      this.name = name;
      this.fromVar = fromVar;
   }

   @Override
   public void run(Session session) {
      Object object = fromVar.getObject(session);
      object = SharedDataHelper.unwrapVars(session, object);
      session.agentData().push(session, name, object);
   }

   /**
    * Makes the data available to all sessions in the same agent, including those using different executors.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("publishAgentData")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String name;
      private String fromVar;

      /**
       * @param param Both name of source variable and the key used to read the data.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return name(param).fromVar(param);
      }

      /**
       * Arbitrary unique identifier for the data.
       *
       * @param name Identifier.
       * @return Self.
       */
      public Builder name(String name) {
         this.name = name;
         return this;
      }

      /**
       * Source session variable name.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      @Override
      public PublishAgentDataAction build() {
         if (name == null || name.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid name: " + name);
         } else if (fromVar == null) {
            throw new BenchmarkDefinitionException("Must set 'fromVar'");
         }
         return new PublishAgentDataAction(name, SessionFactory.readAccess(fromVar));
      }
   }
}
