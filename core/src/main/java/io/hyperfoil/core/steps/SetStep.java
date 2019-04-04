package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class SetStep implements Action.Step, ResourceUtilizer {
   private final Access var;
   private final String value;

   public SetStep(String var, String value) {
      this.var = SessionFactory.access(var);
      this.value = value;
   }

   @Override
   public void run(Session session) {
      var.setObject(session, value);
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
   }

   public static class Builder extends BaseStepBuilder implements Action.Builder {
      private String var;
      private String value;

      public Builder(String param) {
         super(null);
         int sep = param.indexOf("<-");
         if (sep < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition '" + param + "': should be 'var <- value'");
         }
         this.var = param.substring(0, sep).trim();
         this.value = param.substring(sep + 2).trim();
      }

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public SetStep.Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder value(String value) {
         this.value = value;
         return this;
      }

      @Override
      public SetStep build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Variable name was not set!");
         }
         if (value == null) {
            throw new BenchmarkDefinitionException("Value was not set!");
         }
         return new SetStep(var, value);
      }

      @Override
      public void prepareBuild() {
         // We need to override unrelated default methods
      }

      @Override
      public List<io.hyperfoil.api.config.Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(build());
      }
   }

   @MetaInfServices(Action.BuilderFactory.class)
   public static class ActionFactory implements Action.BuilderFactory {
      @Override
      public String name() {
         return "set";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      @Override
      public SetStep.Builder newBuilder(Locator locator, String param) {
         return new SetStep.Builder(param);
      }
   }
}
