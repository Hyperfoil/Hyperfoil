package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiPredicate;

public class StringCondition implements Condition {
   private final Access fromVar;
   private final boolean isSet;
   private final SerializableBiPredicate<Session, CharSequence> predicate;

   public StringCondition(Access fromVar, boolean isSet, SerializableBiPredicate<Session, CharSequence> predicate) {
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
      CharSequence value;
      if (var.type() == Session.VarType.INTEGER) {
         value = String.valueOf(var.intValue(session));
      } else if (var.type() == Session.VarType.OBJECT) {
         Object obj = var.objectValue(session);
         if (!(obj instanceof CharSequence)) {
            return false;
         }
         value = (CharSequence) obj;
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
      return predicate.test(session, value);
   }

   public static class Builder<P> extends StringConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>> {
      private Object fromVar;
      private boolean isSet;

      public Builder(P parent) {
         super(parent);
      }

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

      public StringCondition buildCondition() {
         return new StringCondition(SessionFactory.access(fromVar), isSet, isSet ? buildPredicate() : null);
      }
   }
}
