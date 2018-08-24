package io.sailrocket.core.session;

import io.sailrocket.api.Session;

public class ObjectVar implements Session.Var {
   boolean set;
   Object value;

   public static ObjectVar[] newArray(Session session, int size) {
      ObjectVar[] array = new ObjectVar[size];
      for (int i = 0; i < array.length; ++i) array[i] = new ObjectVar((SessionImpl) session);
      return array;
   }

   ObjectVar(SessionImpl session) {
      session.registerVar(this);
   }

   @Override
   public boolean isSet() {
      return set;
   }

   @Override
   public void unset() {
      set = false;
   }

   public Object get() {
      assert set;
      return value;
   }

   public void set(Object value) {
      this.value = value;
      this.set = true;
   }
}
