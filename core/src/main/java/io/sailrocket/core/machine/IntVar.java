package io.sailrocket.core.machine;

import io.sailrocket.api.Session;

public class IntVar implements Session.Var {
   private boolean set;
   private int value;

   public IntVar(io.sailrocket.core.machine.Session session) {
      session.registerVar(this);
   }

   public static IntVar[] newArray(io.sailrocket.core.machine.Session session, int size) {
      IntVar[] array = new IntVar[size];
      for (int i = 0; i < array.length; ++i) array[i] = new IntVar(session);
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
}
