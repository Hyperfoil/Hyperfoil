package io.hyperfoil.core.steps.collections;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.StringConditionBase;
import io.hyperfoil.core.builders.StringConditionBuilder;
import io.hyperfoil.function.SerializableBiPredicate;

public class StringFilter extends StringConditionBase {
   public StringFilter(SerializableBiPredicate<Session, CharSequence> predicate) {
      super(predicate);
   }

   public static class Builder<P> extends StringConditionBuilder<Builder<P>, P> {
      public Builder(P parent) {
         super(parent);
      }

      public StringFilter build() {
         return new StringFilter(buildPredicate());
      }
   }
}
