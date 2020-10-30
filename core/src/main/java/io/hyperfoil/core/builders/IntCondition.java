package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableIntPredicate;

public class IntCondition implements Condition {
   private final Access fromVar;
   private final SerializableIntPredicate predicate;

   public IntCondition(Access fromVar, SerializableIntPredicate predicate) {
      this.fromVar = fromVar;
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
   public static class Builder<P> extends IntConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>> {
      private String fromVar;

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
      public Builder<P> fromVar(String var) {
         this.fromVar = var;
         return this;
      }

      @Override
      public IntCondition buildCondition() {
         return new IntCondition(SessionFactory.access(fromVar), buildPredicate());
      }
   }

   public static class ProvidedVarBuilder<P> extends IntConditionBuilder<ProvidedVarBuilder<P>, P> {
      public ProvidedVarBuilder(P parent) {
         super(parent);
      }

      public IntCondition build(String var) {
         return new IntCondition(SessionFactory.access(var), buildPredicate());
      }
   }

}
