package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
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
   public static class Builder extends ActionStepBuilder {
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder(String param) {
         super(null);
         var = param;
      }

      /**
       * Variable name.
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

   @MetaInfServices(Action.BuilderFactory.class)
   public static class ActionFactory implements Action.BuilderFactory {
      @Override
      public String name() {
         return "unset";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      /**
       * @param param Variable name.
       */
      @Override
      public UnsetStep.Builder newBuilder(Locator locator, String param) {
         return new Builder(param);
      }
   }
}
