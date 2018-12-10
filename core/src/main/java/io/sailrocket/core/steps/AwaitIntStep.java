package io.sailrocket.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;

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

   public static class Builder extends BaseStepBuilder {
      private String var;
      private Integer equalTo;
      private Integer notEqualTo;
      private Integer greaterThan;
      private Integer greaterOrEqualTo;
      private Integer lessThan;
      private Integer lessOrEqualTo;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder equalTo(int equalTo) {
         this.equalTo = equalTo;
         return this;
      }

      public Builder notEqualTo(int notEqualTo) {
         this.notEqualTo = notEqualTo;
         return this;
      }

      public Builder greaterThan(int greaterThan) {
         this.greaterThan = greaterThan;
         return this;
      }

      public Builder greaterOrEqualTo(int greaterOrEqualTo) {
         this.greaterOrEqualTo = greaterOrEqualTo;
         return this;
      }

      public Builder lessThan(int lessThan) {
         this.lessThan = lessThan;
         return this;
      }

      public Builder lessOrEqualTo(int lessOrEqualTo) {
         this.lessOrEqualTo = lessOrEqualTo;
         return this;
      }

      @Override
      public List<Step> build() {
         IntPredicate predicate = null;
         if (equalTo != null) {
            predicate = and(predicate, v -> v == equalTo.intValue());
         }
         if (notEqualTo != null) {
            predicate = and(predicate, v -> v != notEqualTo.intValue());
         }
         if (greaterThan != null) {
            predicate = and(predicate, v -> v > greaterThan.intValue());
         }
         if (greaterOrEqualTo != null) {
            predicate = and(predicate, v -> v >= greaterOrEqualTo.intValue());
         }
         if (lessThan != null) {
            predicate = and(predicate, v -> v < lessThan.intValue());
         }
         if (lessOrEqualTo != null) {
            predicate = and(predicate, v -> v <= lessOrEqualTo.intValue());
         }
         return Collections.singletonList(new AwaitIntStep(var, predicate));
      }

      private static IntPredicate and(IntPredicate p1, IntPredicate p2) {
         return p1 == null ? p2 : (p2 == null ? null : p1.and(p2));
      }
   }
}
