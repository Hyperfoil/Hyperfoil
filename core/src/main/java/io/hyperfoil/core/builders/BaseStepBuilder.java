package io.hyperfoil.core.builders;

import java.util.Objects;

import io.hyperfoil.api.config.BaseBuilder;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.api.config.StepBuilder;

public abstract class BaseStepBuilder<T extends BaseStepBuilder<T>> extends BaseBuilder implements StepBuilder<T> {

   private BaseSequenceBuilder<?> parent;
   private boolean prepared = false;

   @Override
   public final void prepareBuild() {
      if (prepared) {
         return;
      }
      prepared = true;

      prepare(pluginClass(), this);

      doPrepareBuild();
      StepBuilder.super.prepareBuild();
   }

   @SuppressWarnings("rawtypes")
   protected Class<? extends PluginBuilder> pluginClass() {
      return null;
   }

   public abstract void doPrepareBuild();

   public T addTo(BaseSequenceBuilder<?> parent) {
      if (this.parent != null) {
         throw new UnsupportedOperationException("Cannot add builder " + getClass().getName() + " to another sequence!");
      }
      parent.stepBuilder(this);
      this.parent = Objects.requireNonNull(parent);
      @SuppressWarnings("unchecked")
      T self = (T) this;
      return self;
   }

   public BaseSequenceBuilder<?> endStep() {
      if (parent == null) {
         throw new UnsupportedOperationException("Sequence for " + getClass().getName() + " was not set.");
      }
      return parent;
   }
}
