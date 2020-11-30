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
      return p1 == null ? p2 : (p2 == null ? null : new And(p1, p2));
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
         predicate = new EqualTo(equalTo);
      }
      if (notEqualTo != null) {
         predicate = and(predicate, new NotEqualTo(notEqualTo));
      }
      if (greaterThan != null) {
         predicate = and(predicate, new GreaterThan(greaterThan));
      }
      if (greaterOrEqualTo != null) {
         predicate = and(predicate, new GreaterOrEqualTo(greaterOrEqualTo));
      }
      if (lessThan != null) {
         predicate = and(predicate, new LessThan(lessThan));
      }
      if (lessOrEqualTo != null) {
         predicate = and(predicate, new LessOrEqualTo(lessOrEqualTo));
      }
      return predicate;
   }

   public P end() {
      return parent;
   }

   private static class And implements SerializableIntPredicate {
      private final SerializableIntPredicate p1;
      private final SerializableIntPredicate p2;

      public And(SerializableIntPredicate p1, SerializableIntPredicate p2) {
         this.p1 = p1;
         this.p2 = p2;
      }

      @Override
      public boolean test(int x) {
         return p1.test(x) && p2.test(x);
      }
   }

   private static class EqualTo implements SerializableIntPredicate {
      private final int val;

      public EqualTo(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v == val;
      }
   }

   private static class NotEqualTo implements SerializableIntPredicate {
      private final int val;

      public NotEqualTo(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v != val;
      }
   }

   private static class GreaterThan implements SerializableIntPredicate {
      private final int val;

      public GreaterThan(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v > val;
      }
   }

   private static class GreaterOrEqualTo implements SerializableIntPredicate {
      private final int val;

      public GreaterOrEqualTo(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v >= val;
      }
   }

   private static class LessThan implements SerializableIntPredicate {
      private final int val;

      public LessThan(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v < val;
      }
   }

   private static class LessOrEqualTo implements SerializableIntPredicate {
      private final int val;

      public LessOrEqualTo(int val) {
         this.val = val;
      }

      @Override
      public boolean test(int v) {
         return v <= val;
      }
   }
}
