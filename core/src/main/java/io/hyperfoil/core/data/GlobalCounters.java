package io.hyperfoil.core.data;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.session.GlobalData;

public class GlobalCounters implements GlobalData.Element {
   // we could be more allocation-efficient with arrays? doesn't matter probably
   private final Map<String, Long> counters;

   public GlobalCounters(Map<String, Long> counters) {
      this.counters = counters;
   }

   public long get(String counter) {
      return counters.getOrDefault(counter, 0L);
   }

   public Map<String, Long> getAll() {
      return counters;
   }

   @Override
   public GlobalData.Accumulator newAccumulator() {
      return new Accumulator();
   }

   @Override
   public String toString() {
      return counters.toString();
   }

   private static class Accumulator implements GlobalData.Accumulator {
      private final Map<String, Long> counters = new HashMap<>();

      @Override
      public void add(GlobalData.Element e) {
         if (e instanceof GlobalCounters) {
            GlobalCounters gc = (GlobalCounters) e;
            for (var entry : gc.counters.entrySet()) {
               Long newValue = counters.getOrDefault(entry.getKey(), 0L) + entry.getValue();
               counters.put(entry.getKey(), newValue);
            }
         } else {
            throw new IllegalArgumentException("Expected GlobalCounters, got: " + e);
         }
      }

      @Override
      public GlobalData.Element complete() {
         return new GlobalCounters(counters);
      }
   }
}
