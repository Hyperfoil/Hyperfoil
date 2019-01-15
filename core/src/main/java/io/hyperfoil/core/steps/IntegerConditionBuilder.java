package io.hyperfoil.core.steps;

import java.util.function.IntPredicate;

import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;

public abstract class IntegerConditionBuilder<B extends IntegerConditionBuilder<B>> extends BaseStepBuilder {
   protected Integer equalTo;
   protected Integer notEqualTo;
   protected Integer greaterThan;
   protected Integer greaterOrEqualTo;
   protected Integer lessThan;
   protected Integer lessOrEqualTo;

   public IntegerConditionBuilder(BaseSequenceBuilder parent) {
      super(parent);
   }

   protected static IntPredicate and(IntPredicate p1, IntPredicate p2) {
      return p1 == null ? p2 : (p2 == null ? null : p1.and(p2));
   }

   private B self() {
      return (B) this;
   }

   public B equalTo(int equalTo) {
      this.equalTo = equalTo;
      return self();
   }

   public B notEqualTo(int notEqualTo) {
      this.notEqualTo = notEqualTo;
      return self();
   }

   public B greaterThan(int greaterThan) {
      this.greaterThan = greaterThan;
      return self();
   }

   public B greaterOrEqualTo(int greaterOrEqualTo) {
      this.greaterOrEqualTo = greaterOrEqualTo;
      return self();
   }

   public B lessThan(int lessThan) {
      this.lessThan = lessThan;
      return self();
   }

   public B lessOrEqualTo(int lessOrEqualTo) {
      this.lessOrEqualTo = lessOrEqualTo;
      return self();
   }

   protected IntPredicate buildPredicate() {
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
      return predicate;
   }
}
