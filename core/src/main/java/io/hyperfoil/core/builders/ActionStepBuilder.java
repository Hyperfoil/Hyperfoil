package io.hyperfoil.core.builders;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.function.SerializableSupplier;

public abstract class ActionStepBuilder extends BaseStepBuilder implements Action.Builder {
   protected ActionStepBuilder(BaseSequenceBuilder parent) {
      super(parent);
   }

   @Override
   public void prepareBuild() {
      // noop in both parent
   }

   @Override
   public abstract Action.Step build();

   @Override
   public List<Step> build(SerializableSupplier<Sequence> sequence) {
      return Collections.singletonList(build());
   }
}
