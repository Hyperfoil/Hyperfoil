package io.hyperfoil.core.generators;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.PairBuilder;

public class WeightedGenerator implements Serializable {
   private final double[] cummulativeProbs;
   private final String[] items;

   public WeightedGenerator(double[] cummulativeProbs, String[] items) {
      this.cummulativeProbs = cummulativeProbs;
      this.items = items;
   }

   public int randomIndex() {
      int index;
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (cummulativeProbs != null) {
         assert cummulativeProbs.length == items.length - 1;
         index = Arrays.binarySearch(cummulativeProbs, random.nextDouble());
         if (index < 0) {
            return -index - 1;
         } else {
            return index;
         }
      } else {
         return random.nextInt(items.length);
      }
   }

   public String randomItem() {
      return items[randomIndex()];
   }

   public String[] items() {
      return items;
   }

   public static class Builder<P> extends PairBuilder.OfDouble implements ListBuilder {
      private final P parent;
      private final Map<String, Double> items = new HashMap<>();

      public Builder(P parent) {
         this.parent = parent;
      }

      public P end() {
         return this.parent;
      }

      @Override
      public void nextItem(String item) {
         items.compute(item, (k, value) -> value != null ? value + 1 : 1);
      }

      /**
       * Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value.
       *
       * @param item   Item.
       * @param weight Weight.
       */
      @Override
      public void accept(String item, Double weight) {
         if (items.putIfAbsent(item, weight) != null) {
            throw new BenchmarkDefinitionException("Duplicate item '" + item + "' in randomItem step!");
         }
      }

      public WeightedGenerator.Builder<P> add(String item, double weight) {
         accept(item, weight);
         return this;
      }

      public WeightedGenerator build() {
         List<String> list = new ArrayList<>();
         double[] cummulativeProbs = null;
         if (items.isEmpty()) {
            throw new BenchmarkDefinitionException("No items to pick from!");
         }
         if (items.values().stream().allMatch(v -> v == 1)) {
            return new WeightedGenerator(null, items.keySet().toArray(new String[0]));
         }
         cummulativeProbs = new double[items.size() - 1];
         double normalizer = items.values().stream().mapToDouble(Double::doubleValue).sum();
         double acc = 0;
         int i = 0;
         for (Map.Entry<String, Double> entry : items.entrySet()) {
            acc += entry.getValue() / normalizer;
            if (i < items.size() - 1) {
               cummulativeProbs[i++] = acc;
            } else {
               assert acc > 0.999 && acc <= 1.001;
            }
            list.add(entry.getKey());
         }
         return new WeightedGenerator(cummulativeProbs, list.toArray(new String[0]));
      }
   }
}
