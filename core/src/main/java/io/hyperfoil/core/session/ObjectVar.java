package io.hyperfoil.core.session;

import io.hyperfoil.api.session.Session;

public class ObjectVar implements Session.Var {
   boolean set;
   Object value;

   public static ObjectVar[] newArray(Session session, int size) {
      ObjectVar[] array = new ObjectVar[size];
      for (int i = 0; i < array.length; ++i)
         array[i] = new ObjectVar((SessionImpl) session);
      return array;
   }

   ObjectVar(SessionImpl session) {
      session.registerVar(this);
   }

   @Override
   public Session.VarType type() {
      return Session.VarType.OBJECT;
   }

   @Override
   public Object objectValue(Session session) {
      return value;
   }

   @Override
   public boolean isSet() {
      return set;
   }

   @Override
   public void unset() {
      set = false;
   }

   public void set(Object value) {
      this.value = value;
      this.set = true;
   }

   @Override
   public String toString() {
      if (set) {
         return "(object:" + value + ")";
      } else {
         return "(object:unset)";
      }
   }
}
