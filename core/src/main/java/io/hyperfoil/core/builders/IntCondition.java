package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableIntPredicate;

public class IntCondition implements Condition {
   private final ReadAccess fromVar;
   private final boolean isSet;
   private final SerializableIntPredicate predicate;

   public IntCondition(ReadAccess fromVar, boolean isSet, SerializableIntPredicate predicate) {
      this.fromVar = fromVar;
      this.isSet = isSet;
      this.predicate = predicate;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         return !isSet;
      } else if (!isSet) {
         return false;
      } else if (predicate == null) {
         return true;
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
   public static class Builder<P> extends IntConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>> {
      private Object fromVar;
      private boolean isSet = true;

      public Builder() {
         this(null);
      }

      public Builder(P parent) {
         super(parent);
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder<P> fromVar(Object var) {
         this.fromVar = var;
         return this;
      }

      /**
       * Check if the value is set or unset. By default the variable must be set.
       *
       * @param isSet True or false.
       * @return Self.
       */
      public Builder<P> isSet(boolean isSet) {
         this.isSet = isSet;
         return this;
      }

      @Override
      public IntCondition buildCondition() {
         return new IntCondition(SessionFactory.readAccess(fromVar), isSet, buildPredicate());
      }
   }

   public static class ProvidedVarBuilder<P> extends IntConditionBuilder<ProvidedVarBuilder<P>, P> {
      public ProvidedVarBuilder(P parent) {
         super(parent);
      }

      public IntCondition build(String var) {
         return new IntCondition(SessionFactory.readAccess(var), true, buildPredicate());
      }
   }

}
