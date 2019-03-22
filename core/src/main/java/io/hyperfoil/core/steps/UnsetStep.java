package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.function.SerializableSupplier;

public class UnsetStep implements Action.Step {
   public final String var;
   public final boolean sequenceScoped;

   public UnsetStep(String var, boolean sequenceScoped) {
      this.var = var;
      this.sequenceScoped = sequenceScoped;
   }

   @Override
   public void run(Session session) {
      if (sequenceScoped) {
         Session.Var sequenceScopedVar = session.getSequenceScopedVar(var);
         sequenceScopedVar.unset();
      } else {
         session.unset(var);
      }
   }

   public static class Builder extends BaseStepBuilder implements Action.Builder {
      private String var;
      private boolean sequenceScoped;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder(String param) {
         super(null);
         var = param;
      }

      public Builder var(String var) {
         this.var = var;
         this.sequenceScoped = false;
         return this;
      }

      public Builder sequenceVar(String var) {
         this.var = var;
         this.sequenceScoped = true;
         return this;
      }

      @Override
      public void prepareBuild() {
         // We need to override unrelated default methods
      }

      @Override
      public List<io.hyperfoil.api.config.Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new UnsetStep(var, sequenceScoped));
      }

      @Override
      public Action build() {
         return new UnsetStep(var, sequenceScoped);
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

      @Override
      public UnsetStep.Builder newBuilder(StepBuilder stepBuilder, String param) {
         return new Builder(param);
      }
   }
}
