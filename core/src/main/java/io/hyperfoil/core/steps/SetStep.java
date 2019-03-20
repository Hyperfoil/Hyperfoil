package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.ServiceLoadedBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.function.SerializableSupplier;

public class SetStep implements Action.Step {
   private final String var;
   private final boolean sequenceScoped;
   private final String value;

   public SetStep(String var, boolean sequenceScoped, String value) {
      this.var = var;
      this.sequenceScoped = sequenceScoped;
      this.value = value;
   }

   @Override
   public void run(Session session) {
      if (sequenceScoped) {
         Object holder = session.activate(var);
         if (!(holder instanceof ObjectVar[])) {
            throw new IllegalStateException(var + " does not hold a sequence-scoped var!");
         }
         ObjectVar[] vars = (ObjectVar[]) holder;
         int index = session.currentSequence().index();
         if (index >= vars.length) {
            throw new IllegalStateException("Sequence-scoped var is too short (" + vars.length + ") for this sequence!");
         }
         vars[index].set(value);
      } else {
         session.setObject(var, value);
      }
   }

   public static class Builder extends ServiceLoadedBuilder.Base<Action> implements StepBuilder {
      private final BaseSequenceBuilder parent;
      private String var;
      private boolean sequenceScoped;
      private String value;

      public Builder(Consumer<Action> buildTarget, String param) {
         super(buildTarget);
         this.parent = null;
         int sep = param.indexOf("<-");
         if (sep < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition '" + param + "': should be 'var <- value'");
         }
         this.var = param.substring(0, sep).trim();
         this.value = param.substring(sep + 2).trim();
      }

      public Builder(BaseSequenceBuilder parent) {
         super(null);
         this.parent = parent;
         parent.stepBuilder(this);
      }

      public SetStep.Builder var(String var) {
         this.var = var;
         this.sequenceScoped = false;
         return this;
      }

      public SetStep.Builder sequenceVar(String var) {
         this.var = var;
         this.sequenceScoped = true;
         return this;
      }

      public Builder value(String value) {
         this.value = value;
         return this;
      }

      @Override
      protected SetStep build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Variable name was not set!");
         }
         if (value == null) {
            throw new BenchmarkDefinitionException("Value was not set!");
         }
         return new SetStep(var, sequenceScoped, value);
      }

      @Override
      public List<io.hyperfoil.api.config.Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new UnsetStep(var, sequenceScoped));
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
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
      public SetStep.Builder newBuilder(StepBuilder stepBuilder, Consumer<Action> buildTarget, String param) {
         return new SetStep.Builder(buildTarget, param);
      }
   }
}
