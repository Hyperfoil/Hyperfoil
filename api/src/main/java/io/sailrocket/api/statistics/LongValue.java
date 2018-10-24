package io.sailrocket.api.statistics;

public class LongValue implements CustomValue {
   private long value;

   public void add(int increment) {
      value += increment;
   }

   @Override
   public void add(CustomValue other) {
      value += val(other);
   }

   @Override
   public void substract(CustomValue other) {
      value -= val(other);
   }

   @Override
   public void reset() {
      value = 0;
   }

   @Override
   public CustomValue clone() {
      LongValue clone = new LongValue();
      clone.value = value;
      return clone;
   }

   private long val(CustomValue other) {
      if (other instanceof LongValue) {
         return ((LongValue) other).value;
      } else {
         throw new IllegalArgumentException(String.valueOf(other));
      }
   }

   public long value() {
      return value;
   }
}
