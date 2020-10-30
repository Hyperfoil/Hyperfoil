package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiPredicate;

public class StringCondition implements Condition {
   private final Access fromVar;
   private final SerializableBiPredicate<Session, CharSequence> predicate;

   public StringCondition(Access fromVar, SerializableBiPredicate<Session, CharSequence> predicate) {
      this.fromVar = fromVar;
      this.predicate = predicate;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      if (!var.isSet()) {
         return false;
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
      private String fromVar;

      public Builder(P parent) {
         super(parent);
      }

      public Builder<P> fromVar(String var) {
         this.fromVar = var;
         return this;
      }

      public StringCondition buildCondition() {
         return new StringCondition(SessionFactory.access(fromVar), buildPredicate());
      }
   }
}
