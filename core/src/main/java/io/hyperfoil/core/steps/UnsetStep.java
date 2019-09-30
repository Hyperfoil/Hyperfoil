package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.ActionStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class UnsetStep implements Action.Step {
   public final Access var;

   public UnsetStep(String var) {
      this.var = SessionFactory.access(var);
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
   public static class Builder extends ActionStepBuilder implements InitFromParam<Builder> {
      private String var;

      public Builder() {
      }

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
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
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public UnsetStep build() {
         return new UnsetStep(var);
      }
   }
}
