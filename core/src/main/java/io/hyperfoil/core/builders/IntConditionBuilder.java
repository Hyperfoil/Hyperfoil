package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableToIntFunction;

public class IntConditionBuilder<B extends IntConditionBuilder<B, P>, P> implements BuilderBase<B> {
   protected final P parent;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> equalTo;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> notEqualTo;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> greaterThan;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> greaterOrEqualTo;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> lessThan;
   protected IntSourceBuilder<IntConditionBuilder<B, P>> lessOrEqualTo;

   protected static IntCondition.Predicate and(IntCondition.Predicate p1, IntCondition.Predicate p2) {
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
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> equalTo() {
      return equalTo = new IntSourceBuilder<>(this);
   }

   /**
    * Compared variable must not be equal to this value.
    *
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> notEqualTo() {
      return notEqualTo = new IntSourceBuilder<>(this);
   }

   /**
    * Compared variable must be greater than this value.
    *
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> greaterThan() {
      return greaterThan = new IntSourceBuilder<>(this);
   }

   /**
    * Compared variable must be greater or equal to this value.
    *
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> greaterOrEqualTo() {
      return greaterOrEqualTo = new IntSourceBuilder<>(this);
   }

   /**
    * Compared variable must be lower than this value.
    *
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> lessThan() {
      return lessThan = new IntSourceBuilder<>(this);
   }

   /**
    * Compared variable must be lower or equal to this value.
    *
    * @return Builder.
    */
   public IntSourceBuilder<IntConditionBuilder<B, P>> lessOrEqualTo() {
      return lessOrEqualTo = new IntSourceBuilder<>(this);
   }

   protected IntCondition.Predicate buildPredicate() {
      IntCondition.Predicate predicate = null;
      if (equalTo != null) {
         predicate = new EqualTo(equalTo.build());
      }
      if (notEqualTo != null) {
         predicate = and(predicate, new NotEqualTo(notEqualTo.build()));
      }
      if (greaterThan != null) {
         predicate = and(predicate, new GreaterThan(greaterThan.build()));
      }
      if (greaterOrEqualTo != null) {
         predicate = and(predicate, new GreaterOrEqualTo(greaterOrEqualTo.build()));
      }
      if (lessThan != null) {
         predicate = and(predicate, new LessThan(lessThan.build()));
      }
      if (lessOrEqualTo != null) {
         predicate = and(predicate, new LessOrEqualTo(lessOrEqualTo.build()));
      }
      return predicate;
   }

   public P end() {
      return parent;
   }

   private static class And implements IntCondition.Predicate {
      private final IntCondition.Predicate p1;
      private final IntCondition.Predicate p2;

      public And(IntCondition.Predicate p1, IntCondition.Predicate p2) {
         this.p1 = p1;
         this.p2 = p2;
      }

      @Override
      public boolean test(Session session, int x) {
         return p1.test(session, x) && p2.test(session, x);
      }
   }

   private static class EqualTo implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public EqualTo(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v == supplier.applyAsInt(session);
      }
   }

   private static class NotEqualTo implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public NotEqualTo(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v != supplier.applyAsInt(session);
      }
   }

   private static class GreaterThan implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public GreaterThan(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v > supplier.applyAsInt(session);
      }
   }

   private static class GreaterOrEqualTo implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public GreaterOrEqualTo(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v >= supplier.applyAsInt(session);
      }
   }

   private static class LessThan implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public LessThan(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v < supplier.applyAsInt(session);
      }
   }

   private static class LessOrEqualTo implements IntCondition.Predicate {
      private final SerializableToIntFunction<Session> supplier;

      public LessOrEqualTo(SerializableToIntFunction<Session> supplier) {
         this.supplier = supplier;
      }

      @Override
      public boolean test(Session session, int v) {
         return v <= supplier.applyAsInt(session);
      }
   }
}
