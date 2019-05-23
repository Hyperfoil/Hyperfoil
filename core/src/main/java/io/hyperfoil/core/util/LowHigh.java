package io.hyperfoil.core.util;

public class LowHigh {
   public final int low;
   public final int high;

   public LowHigh(int low, int high) {
      this.high = high;
      this.low = low;
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
