package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ActionStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class SetIntStep implements Action.Step, ResourceUtilizer {
   private final Access var;
   private final int value;

   public SetIntStep(String var, int value) {
      this.var = SessionFactory.access(var);
      this.value = value;
   }

   @Override
   public void run(Session session) {
      var.setInt(session, value);
   }

   @Override
   public void reserve(Session session) {
      var.declareInt(session);
   }

   public static class Builder extends ActionStepBuilder {
      private String var;
      private int value;

      public Builder(BaseSequenceBuilder parent, String param) {
         super(parent);
         if (param != null) {
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
         }
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder value(int value) {
         this.value = value;
         return this;
      }

      @Override
      public SetIntStep build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("No variable set!");
         }
         return new SetIntStep(var, value);
      }
   }

   @MetaInfServices(Action.BuilderFactory.class)
   public static class ActionFactory implements Action.BuilderFactory {
      @Override
      public String name() {
         return "setInt";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder(null, param);
      }
   }
}
