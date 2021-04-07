package io.hyperfoil.api.statistics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonTypeName;

@MetaInfServices(StatsExtension.class)
@JsonTypeName("counters")
public class Counters implements StatsExtension {
   final Map<Object, Integer> indices;
   long[] counters;

   public Counters() {
      this(new HashMap<>(), new long[8]);
   }

   public Counters(Map<Object, Integer> indices, long[] counters) {
      this.indices = indices;
      this.counters = counters;
   }

   @Override
   public boolean isNull() {
      for (int i = 0; i < counters.length; ++i) {
         if (counters[i] > 0) {
            return false;
         }
      }
      return true;
   }

   public void increment(Object name) {
      int index = getIndex(name);
      counters[index]++;
   }

   private int getIndex(Object header) {
      Integer currentIndex = indices.get(header);
      if (currentIndex == null) {
         int nextIndex = indices.size();
         if (nextIndex == counters.length) {
            counters = Arrays.copyOf(counters, counters.length * 2);
         }
         currentIndex = nextIndex;
         indices.put(header, currentIndex);
      }
      return currentIndex;
   }

   @Override
   public void add(StatsExtension other) {
      if (other instanceof Counters) {
         Counters o = (Counters) other;
         for (Object header : o.indices.keySet()) {
            int index = getIndex(header);
            counters[index] += o.counters[o.indices.get(header)];
         }
      } else {
         throw new IllegalArgumentException(other.toString());
      }
   }

   @Override
   public void subtract(StatsExtension other) {
      if (other instanceof Counters) {
         Counters o = (Counters) other;
         for (Object header : o.indices.keySet()) {
            int index = getIndex(header);
            counters[index] -= o.counters[o.indices.get(header)];
         }
      } else {
         throw new IllegalArgumentException(other.toString());
      }
   }

   @Override
   public void reset() {
      Arrays.fill(counters, 0);
   }

   @SuppressWarnings("MethodDoesntCallSuperMethod")
   @Override
   public StatsExtension clone() {
      return new Counters(new HashMap<>(indices), Arrays.copyOf(counters, counters.length));
   }

   @Override
   public String[] headers() {
      return indices.keySet().stream().map(Object::toString).toArray(String[]::new);
   }

   @Override
   public String byHeader(String header) {
      for (var entry : indices.entrySet()) {
         if (entry.getKey().toString().equals(header)) {
            return String.valueOf(counters[entry.getValue()]);
         }
      }
      return "<unknown header: " + header + ">";
   }

   @JsonAnyGetter
   public Map<String, Long> serialize() {
      return indices.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), e -> counters[e.getValue()]));
   }

   @JsonAnySetter
   public void set(String key, long value) {
      int index = getIndex(key);
      counters[index] = value;
   }
}
