package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class AddToIntAction implements Action {
   protected final IntAccess var;
   protected final int value;
   protected final Integer orElseSetTo;

   public AddToIntAction(IntAccess var, int value, Integer orElseSetTo) {
      this.var = var;
      this.value = value;
      this.orElseSetTo = orElseSetTo;
   }

   @Override
   public void run(Session session) {
      if (orElseSetTo != null && !var.isSet(session)) {
         var.setInt(session, orElseSetTo);
      } else {
         int prev = this.var.getInt(session);
         this.var.setInt(session, prev + value);
      }
   }

   /**
    * Add value to integer variable in the session.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("addToInt")
   public static class Builder implements InitFromParam<Builder>, Action.Builder {
      private String var;
      private int value;
      private Integer orElseSetTo;

      public Builder() {
      }

      /**
       * @param param Accepting one of: <code>var++</code>, <code>var--</code>, <code>var += value</code>, <code>var -= value</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
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
            value = Integer.parseInt(param.substring(plusEqualsIndex + 2).trim());
         } else if (param.contains("-=")) {
            int minusEqualsIndex = param.indexOf("-=");
            var = param.substring(0, minusEqualsIndex).trim();
            value = -Integer.parseInt(param.substring(minusEqualsIndex + 2).trim());
         } else {
            throw new BenchmarkDefinitionException("Accepting one of: var++, var--, var += value, var -= value");
         }
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

      /**
       * If the variable is currently not set, set it to this value instead of addition.
       *
       * @param value New value.
       * @return Self.
       */
      public Builder orElseSetTo(int value) {
         orElseSetTo = value;
         return this;
      }

      @Override
      public AddToIntAction build() {
         if (var == null || var.isEmpty()) {
            throw new BenchmarkDefinitionException("Var must be defined an not empty.");
         }
         if (value == 0) {
            throw new BenchmarkDefinitionException("It makes no sense to add 0.");
         }
         return new AddToIntAction(SessionFactory.intAccess(var), value, orElseSetTo);
      }
   }
}
