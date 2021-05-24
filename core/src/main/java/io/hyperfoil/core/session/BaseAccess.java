package io.hyperfoil.core.session;

import java.util.Objects;

import io.hyperfoil.api.session.ReadAccess;

public abstract class BaseAccess implements ReadAccess {
   protected final Object key;
   protected int index = -1;

   public BaseAccess(Object key) {
      this.key = Objects.requireNonNull(key);
   }

   @Override
   public Object key() {
      return key;
   }

   @Override
   public void setIndex(int index) {
      assert this.index < 0 || this.index == index : "Current index " + this.index + ", suggested index " + index;
      this.index = index;
   }

   @Override
   public int index() {
      return index;
   }
}

