package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.function.SerializableIntPredicate;
import io.hyperfoil.function.SerializablePredicate;

public class IntCondition implements SerializablePredicate<Session> {
   private final String var;
   private final SerializableIntPredicate predicate;

   public IntCondition(String var, SerializableIntPredicate predicate) {
      this.var = var;
      this.predicate = predicate;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = session.getVar(this.var);
      if (!var.isSet()) {
         return false;
      }
      int value;
      if (var instanceof IntVar) {
         value = var.intValue();
      } else if (var instanceof ObjectVar) {
         value = Integer.parseInt(var.objectValue().toString());
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
      return predicate.test(value);
   }

   public static class Builder<P> extends BaseBuilder<Builder<P>> implements Condition.Builder {
      private final P parent;
      private String var;

      public Builder(P parent) {
         this.parent = parent;
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public P endCondition() {
         return parent;
      }

      @Override
      public IntCondition build() {
         return new IntCondition(var, buildPredicate());
      }
   }

   public abstract static class BaseBuilder<B extends BaseBuilder<B>> {
      protected Integer equalTo;
      protected Integer notEqualTo;
      protected Integer greaterThan;
      protected Integer greaterOrEqualTo;
      protected Integer lessThan;
      protected Integer lessOrEqualTo;

      protected static SerializableIntPredicate and(SerializableIntPredicate p1, SerializableIntPredicate p2) {
         return p1 == null ? p2 : (p2 == null ? null : x -> p1.test(x) && p2.test(x));
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

      protected SerializableIntPredicate buildPredicate() {
         SerializableIntPredicate predicate = null;
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
}
