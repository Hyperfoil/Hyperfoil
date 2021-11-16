package io.hyperfoil.core.steps.data;

import java.util.Objects;

import org.kohsuke.MetaInfServices;

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

   @MetaInfServices(Action.Builder.class)
   @Name("publishAgentData")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String name;
      private String fromVar;

      @Override
      public Builder init(String param) {
         return name(param).fromVar(param);
      }

      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      @Override
      public PublishAgentDataAction build() {
         return new PublishAgentDataAction(Objects.requireNonNull(name), SessionFactory.readAccess(Objects.requireNonNull(fromVar)));
      }
   }
}
