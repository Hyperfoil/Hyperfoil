package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class UnsetStep implements Action.Step {
   public final Access var;

   public UnsetStep(String var) {
      this.var = SessionFactory.access(var);
   }

   @Override
   public void run(Session session) {
      var.unset(session);
   }

   public static class Builder extends BaseStepBuilder implements Action.Builder {
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder(String param) {
         super(null);
         var = param;
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public void prepareBuild() {
         // We need to override unrelated default methods
      }

      @Override
      public List<io.hyperfoil.api.config.Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new UnsetStep(var));
      }

      @Override
      public Action build() {
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

      @Override
      public UnsetStep.Builder newBuilder(Locator locator, String param) {
         return new Builder(param);
      }
   }
}
