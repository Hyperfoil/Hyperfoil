package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.function.SerializableIntPredicate;

public class IntConditionBuilder<B extends IntConditionBuilder<B, P>, P> implements BuilderBase<B> {
   protected final P parent;
   protected Integer equalTo;
   protected Integer notEqualTo;
   protected Integer greaterThan;
   protected Integer greaterOrEqualTo;
   protected Integer lessThan;
   protected Integer lessOrEqualTo;

   protected static SerializableIntPredicate and(SerializableIntPredicate p1, SerializableIntPredicate p2) {
      return p1 == null ? p2 : (p2 == null ? null : x -> p1.test(x) && p2.test(x));
   }

   public IntConditionBuilder() {
      this(null);
   }

   public IntConditionBuilder(P parent) {
      this.parent = parent;
   }

   @SuppressWarnings("unchecked")
   private B self() {
      return (B) this;
   }

   /**
    * Compared variable must be equal to this value.
    *
    * @param equalTo Value.
    * @return Self.
    */
   public B equalTo(int equalTo) {
      this.equalTo = equalTo;
      return self();
   }

   /**
    * Compared variable must not be equal to this value.
    *
    * @param notEqualTo Value.
    * @return Self.
    */
   public B notEqualTo(int notEqualTo) {
      this.notEqualTo = notEqualTo;
      return self();
   }

   /**
    * Compared variable must be greater than this value.
    *
    * @param greaterThan Value.
    * @return Self.
    */
   public B greaterThan(int greaterThan) {
      this.greaterThan = greaterThan;
      return self();
   }

   /**
    * Compared variable must be greater or equal to this value.
    *
    * @param greaterOrEqualTo Value.
    * @return Self.
    */
   public B greaterOrEqualTo(int greaterOrEqualTo) {
      this.greaterOrEqualTo = greaterOrEqualTo;
      return self();
   }

   /**
    * Compared variable must be lower than this value.
    *
    * @param lessThan Value.
    * @return Self.
    */
   public B lessThan(int lessThan) {
      this.lessThan = lessThan;
      return self();
   }

   /**
    * Compared variable must be lower or equal to this value.
    *
    * @param lessOrEqualTo Value.
    * @return Self.
    */
   public B lessOrEqualTo(int lessOrEqualTo) {
      this.lessOrEqualTo = lessOrEqualTo;
      return self();
   }

   protected SerializableIntPredicate buildPredicate() {
      SerializableIntPredicate predicate = null;
      if (equalTo != null) {
         int val = equalTo;
         predicate = v -> v == val;
      }
      if (notEqualTo != null) {
         int val = notEqualTo;
         predicate = and(predicate, v -> v != val);
      }
      if (greaterThan != null) {
         int val = greaterThan;
         predicate = and(predicate, v -> v > val);
      }
      if (greaterOrEqualTo != null) {
         int val = greaterOrEqualTo;
         predicate = and(predicate, v -> v >= val);
      }
      if (lessThan != null) {
         int val = lessThan;
         predicate = and(predicate, v -> v < val);
      }
      if (lessOrEqualTo != null) {
         int val = lessOrEqualTo;
         predicate = and(predicate, v -> v <= val);
      }
      return predicate;
   }

   public P end() {
      return parent;
   }
}
