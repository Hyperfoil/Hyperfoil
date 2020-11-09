package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class UnsetAction implements Action {
   public final Access var;

   public UnsetAction(Access var) {
      this.var = var;
   }

   @Override
   public void run(Session session) {
      var.unset(session);
   }

   /**
    * Undefine variable name.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("unset")
   public static class Builder implements InitFromParam<Builder>, Action.Builder {
      private Object var;

      public Builder() {
      }

      /**
       * @param param Variable name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         var = param;
         return this;
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder var(Object var) {
         this.var = var;
         return this;
      }

      @Override
      public UnsetAction build() {
         return new UnsetAction(SessionFactory.access(var));
      }
   }
}
