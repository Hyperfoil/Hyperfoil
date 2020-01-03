package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableIntPredicate;
import io.hyperfoil.function.SerializablePredicate;

public class IntCondition implements SerializablePredicate<Session> {
   private final Access fromVar;
   private final SerializableIntPredicate predicate;

   public IntCondition(String fromVar, SerializableIntPredicate predicate) {
      this.fromVar = SessionFactory.access(fromVar);
      this.predicate = predicate;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         return false;
      }
      int value;
      if (var.type() == VarType.INTEGER) {
         value = var.intValue(session);
      } else if (var.type() == VarType.OBJECT) {
         value = Integer.parseInt(var.objectValue(session).toString());
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
      return predicate.test(value);
   }

   /**
    * Condition comparing integer in session variable.
    */
   public static class Builder<P> extends BaseBuilder<Builder<P>, P> implements Condition.Builder {
      private String fromVar;

      public Builder(P parent) {
         super(parent);
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder<P> fromVar(String var) {
         this.fromVar = var;
         return this;
      }

      @Override
      public IntCondition build() {
         return new IntCondition(fromVar, buildPredicate());
      }
   }

   public static class ProvidedVarBuilder<P> extends BaseBuilder<ProvidedVarBuilder<P>, P> {
      public ProvidedVarBuilder(P parent) {
         super(parent);
      }

      public IntCondition build(String var) {
         return new IntCondition(var, buildPredicate());
      }
   }

   public abstract static class BaseBuilder<B extends BaseBuilder<B, P>, P> {
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

      public BaseBuilder(P parent) {
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
}
