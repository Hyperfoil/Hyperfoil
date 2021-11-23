package io.hyperfoil.core.steps.collections;

import io.hyperfoil.core.builders.BoolConditionBase;
import io.hyperfoil.core.builders.BoolConditionBuilder;

public class BoolFilter extends BoolConditionBase {
   public BoolFilter(boolean value) {
      super(value);
   }

   public static class Builder<P> extends BoolConditionBuilder<Builder<P>, P> {
      public Builder(P parent) {
         super(parent);
      }

      public BoolFilter build() {
         return new BoolFilter(value);
      }
   }
}
