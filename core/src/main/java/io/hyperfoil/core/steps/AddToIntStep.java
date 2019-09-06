package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ActionStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class AddToIntStep implements Action.Step {
   public final Access var;
   public final int value;

   public AddToIntStep(String var, int value) {
      this.var = SessionFactory.access(var);
      this.value = value;
   }

   @Override
   public void run(Session session) {
      int prev = var.getInt(session);
      var.setInt(session, prev + value);
   }

   /**
    * Add value to integer variable in the session.
    */
   public static class Builder extends ActionStepBuilder {
      private String var;
      private int value;

      public Builder(BaseSequenceBuilder parent, String param) {
         super(parent);
         if (param == null) {
            return;
         }
         param = param.trim();
         if (param.endsWith("++")) {
            var = param.substring(0, param.length() - 2).trim();
            value = 1;
         } else if (param.endsWith("--")) {
            var = param.substring(0, param.length() - 2).trim();
            value = -1;
         } else if (param.contains("+=")) {
            int plusEqualsIndex = param.indexOf("+=");
            var = param.substring(0, plusEqualsIndex).trim();
            value = Integer.parseInt(param.substring(plusEqualsIndex).trim());
         } else if (param.contains("-=")) {
            int minusEqualsIndex = param.indexOf("-=");
            var = param.substring(0, minusEqualsIndex).trim();
            value = -Integer.parseInt(param.substring(minusEqualsIndex).trim());
         } else {
            throw new BenchmarkDefinitionException("Accepting one of: var++, var--, var += value, var -= value");
         }
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

      /**
       * Value added (can be negative).
       *
       * @param value Value.
       * @return Self.
       */
      public Builder value(int value) {
         this.value = value;
         return this;
      }

      @Override
      public AddToIntStep build() {
         if (var == null || var.isEmpty()) {
            throw new BenchmarkDefinitionException("Var must be defined an not empty.");
         }
         if (value == 0) {
            throw new BenchmarkDefinitionException("It makes no sense to add 0.");
         }
         return new AddToIntStep(var, value);
      }
   }

   @MetaInfServices(Action.BuilderFactory.class)
   public static class BuilderFactory implements Action.BuilderFactory {
      @Override
      public String name() {
         return "addToInt";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      /**
       * @param locator Locator.
       * @param param Accepting one of: <code>var++</code>, <code>var--</code>, <code>var += value</code>, <code>var -= value</code>.
       * @return Builder.
       */
      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder(null, param);
      }
   }
}
