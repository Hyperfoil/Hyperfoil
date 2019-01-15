package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseSequenceBuilder;

public class AwaitIntStep implements Step {
   private final String var;
   private final IntPredicate predicate;

   public AwaitIntStep(String var, IntPredicate predicate) {
      this.var = var;
      this.predicate = predicate;
   }

   @Override
   public boolean invoke(Session session) {
      if (session.isSet(var)) {
         return predicate == null || predicate.test(session.getInt(var));
      }
      return false;
   }

   public static class Builder extends IntegerConditionBuilder<Builder> {
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new AwaitIntStep(var, buildPredicate()));
      }
   }
}
