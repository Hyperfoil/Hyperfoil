package io.sailrocket.core.session;

import io.sailrocket.api.session.Session;

public class IntVar implements Session.Var {
   private boolean set;
   private int value;

   IntVar(SessionImpl session) {
      session.registerVar(this);
   }

   public static IntVar[] newArray(Session session, int size) {
      IntVar[] array = new IntVar[size];
      for (int i = 0; i < array.length; ++i) array[i] = new IntVar((SessionImpl) session);
      return array;
   }

   @Override
   public boolean isSet() {
      return set;
   }

   @Override
   public void unset() {
      set = false;
   }

   public int get() {
      assert set;
      return value;
   }

   public void set(int value) {
      this.value = value;
      this.set = true;
   }

   public void add(int delta) {
      assert set;
      this.value += delta;
   }
}
