package io.hyperfoil.core.util;

import java.io.Serializable;

public class LowHigh implements Serializable {
   public int low;
   public int high;

   public LowHigh() {
      this(0, 0);
   }

   public LowHigh(int low, int high) {
      this.low = low;
      this.high = high;
   }

   public static LowHigh sum(LowHigh r1, LowHigh r2) {
      if (r1 == null) return r2;
      if (r2 == null) return r1;
      return new LowHigh(r1.low + r2.low, r1.high + r2.high);
   }

   public static LowHigh combine(LowHigh r1, LowHigh r2) {
      if (r1 == null) return r2;
      if (r2 == null) return r1;
      return new LowHigh(Math.min(r1.low, r2.low), Math.max(r1.high, r2.high));
   }
}
