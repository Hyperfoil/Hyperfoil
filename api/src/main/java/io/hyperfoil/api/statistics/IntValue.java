package io.hyperfoil.api.statistics;

public class IntValue implements CustomValue {
   private int value;

   public void add(int increment) {
      value += increment;
   }

   @Override
   public boolean isNull() {
      return value == 0;
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
      IntValue clone = new IntValue();
      clone.value = value;
      return clone;
   }

   private int val(CustomValue other) {
      if (other instanceof IntValue) {
         return ((IntValue) other).value;
      } else {
         throw new IllegalArgumentException(String.valueOf(other));
      }
   }

   public int value() {
      return value;
   }

   @Override
   public String toString() {
      return String.valueOf(value);
   }
}
