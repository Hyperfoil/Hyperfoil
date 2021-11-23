package io.hyperfoil.core.builders;

import java.io.Serializable;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiPredicate;

public abstract class StringConditionBase implements Serializable {
   protected final SerializableBiPredicate<Session, CharSequence> predicate;

   public StringConditionBase(SerializableBiPredicate<Session, CharSequence> predicate) {
      this.predicate = predicate;
   }

   public boolean testVar(Session session, Session.Var var) {
      if (predicate == null) {
         return true;
      }
      CharSequence value;
      if (var.type() == Session.VarType.INTEGER) {
         value = String.valueOf(var.intValue(session));
         return predicate.test(session, value);
      } else if (var.type() == Session.VarType.OBJECT) {
         Object obj = var.objectValue(session);
         return testObject(session, obj);
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
   }

   public boolean testObject(Session session, Object obj) {
      if (!(obj instanceof CharSequence)) {
         return false;
      }
      return predicate.test(session, (CharSequence) obj);
   }

}
