package io.hyperfoil.core.steps.data;

import org.kohsuke.MetaInfServices;

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

   @MetaInfServices(Action.Builder.class)
   @Name("readAgentData")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String name;
      private String toVar;

      @Override
      public Builder init(String param) {
         return name(param).toVar(param);
      }

      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public ReadAgentDataAction build() {
         return new ReadAgentDataAction(name, SessionFactory.objectAccess(toVar));
      }
   }
}
