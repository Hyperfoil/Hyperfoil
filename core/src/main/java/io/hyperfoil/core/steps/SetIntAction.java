package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.session.SessionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SetIntAction implements Action {
   private static final Logger log = LogManager.getLogger(SetIntAction.class);
   private static final boolean trace = log.isTraceEnabled();

   private final IntAccess var;
   private final int value;
   private final boolean onlyIfNotSet;
   private final IntCondition condition;

   public SetIntAction(IntAccess var, int value, boolean onlyIfNotSet, IntCondition condition) {
      this.var = var;
      this.value = value;
      this.onlyIfNotSet = onlyIfNotSet;
      this.condition = condition;
   }

   @Override
   public void run(Session session) {
      if (onlyIfNotSet && var.isSet(session)) {
         if (trace) {
            log.trace("#{} Not setting {} to {} as it is already set to {}", session.uniqueId(), var, value, var.getInt(session));
         }
         return;
      }
      if (condition == null || condition.test(session)) {
         var.setInt(session, value);
      }
   }

   /**
    * Set session variable to an integral value.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("setInt")
   public static class Builder implements InitFromParam<Builder>, Action.Builder {
      private String var;
      private int value;
      private boolean onlyIfNotSet;
      private IntCondition.ProvidedVarBuilder<Builder> intCondition;

      public Builder() {
      }

      /**
       * @param param Use <code>var &lt;- value</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int sep = param.indexOf("<-");
         if (sep < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition '" + param + "': should be 'var <- value'");
         }
         this.var = param.substring(0, sep).trim();
         try {
            this.value = Integer.parseInt(param.substring(sep + 2).trim());
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Cannot parse value as int: " + param.substring(sep + 2), e);
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
       * Value (integer).
       *
       * @param value Value.
       * @return Self.
       */
      public Builder value(int value) {
         this.value = value;
         return this;
      }

      /**
       * Set variable to the value only if it is not already set.
       *
       * @param onlyIfNotSet If false (default) the value is always set.
       * @return Self.
       */
      public Builder onlyIfNotSet(boolean onlyIfNotSet) {
         this.onlyIfNotSet = onlyIfNotSet;
         return this;
      }

      /**
       * Set variable only if the current value satisfies certain condition.
       *
       * @return Builder.
       */
      public IntCondition.ProvidedVarBuilder<Builder> intCondition() {
         return intCondition = new IntCondition.ProvidedVarBuilder<>(this);
      }

      @Override
      public SetIntAction build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("No variable set!");
         }
         return new SetIntAction(SessionFactory.intAccess(var), value, onlyIfNotSet, intCondition == null ? null : intCondition.build(var));
      }
   }
}
