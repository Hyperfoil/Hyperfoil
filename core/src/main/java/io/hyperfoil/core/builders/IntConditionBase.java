package io.hyperfoil.core.builders;

import java.io.Serializable;

import io.hyperfoil.api.session.Session;

public abstract class IntConditionBase implements Serializable {
   protected final IntCondition.Predicate predicate;

   public IntConditionBase(IntCondition.Predicate predicate) {
      this.predicate = predicate;
   }

   public boolean testVar(Session session, Session.Var var) {
      if (predicate == null) {
         return true;
      }
      if (var.type() == Session.VarType.INTEGER) {
         return predicate.test(session, var.intValue(session));
      } else if (var.type() == Session.VarType.OBJECT) {
         return testObject(session, var.objectValue(session));
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
   }

   public boolean testObject(Session session, Object obj) {
      int value;
      if (obj instanceof Integer) {
         value = (Integer) obj;
      } else if (obj instanceof Long) {
         long l = (Long) obj;
         value = (int) Math.min(Math.max(Integer.MIN_VALUE, l), Integer.MAX_VALUE);
      } else {
         value = Integer.parseInt(obj.toString());
      }
      return predicate.test(session, value);
   }
}
