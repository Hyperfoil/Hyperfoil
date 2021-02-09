package io.hyperfoil.http.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.core.util.Util;

public abstract class BaseRangeStatusHandler implements StatusHandler {
   protected final int[] statusRanges;

   public BaseRangeStatusHandler(int[] statusRanges) {
      this.statusRanges = statusRanges;
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      for (int i = 0; 2 * i < statusRanges.length; ++i) {
         if (status >= statusRanges[2 * i] && status <= statusRanges[2 * i + 1]) {
            onStatusRange(request, status, i);
            return;
         }
      }
      onOtherStatus(request, status);
   }

   protected abstract void onStatusRange(HttpRequest request, int status, int index);

   protected abstract void onOtherStatus(HttpRequest request, int status);

   protected static <S, T> T checkAndSortRanges(Map<String, S> map, List<Integer> ranges, List<T> values, Function<S, T> func) {
      T other = null;

      TreeMap<Integer, T> byLow = new TreeMap<>();
      Map<Integer, Integer> toHigh = new HashMap<>();
      for (Map.Entry<String, S> entry : map.entrySet()) {
         if (entry.getKey().equals("other")) {
            other = func.apply(entry.getValue());
            continue;
         }
         for (String part : entry.getKey().split(",")) {
            part = part.trim();
            int low, high;
            try {
               if (part.contains("-")) {
                  int di = part.indexOf('-');
                  low = Integer.parseInt(part.substring(0, di).trim());
                  high = Integer.parseInt(part.substring(di + 1).trim());
               } else {
                  int xn = 0;
                  for (int i = part.length() - 1; i >= 0; --i) {
                     if (part.charAt(i) == 'x') {
                        ++xn;
                     } else break;
                  }
                  int value = Integer.parseInt(part.substring(0, part.length() - xn));
                  int mul = Util.pow(10, xn);
                  low = value * mul;
                  high = (value + 1) * mul - 1;
               }
               if (low > high || low < 100 || high > 599) {
                  throw new BenchmarkDefinitionException("Invalid status range " + low + "-" + high + " in '" + entry.getKey() + "'");
               }
               T partValue = func.apply(entry.getValue());
               Integer floor = byLow.floorKey(low);
               if (floor == null) {
                  Integer ceiling = byLow.ceilingKey(low);
                  if (ceiling != null && ceiling <= high) {
                     throw new BenchmarkDefinitionException("Overlapping ranges: " + low + "-" + high + " and " + ceiling + "-" + toHigh.get(ceiling));
                  }
                  byLow.put(low, partValue);
                  toHigh.put(low, high);
               } else if (floor == low) {
                  throw new BenchmarkDefinitionException("Overlapping ranges: " + low + "-" + high + " and " + floor + "-" + toHigh.get(floor));
               } else {
                  Integer floorHigh = toHigh.get(floor);
                  if (floorHigh >= low) {
                     throw new BenchmarkDefinitionException("Overlapping ranges: " + low + "-" + high + " and " + floor + "-" + floorHigh);
                  }
                  Integer ceiling = byLow.ceilingKey(low);
                  if (ceiling != null && ceiling <= high) {
                     throw new BenchmarkDefinitionException("Overlapping ranges: " + low + "-" + high + " and " + ceiling + "-" + toHigh.get(ceiling));
                  }
                  byLow.put(low, partValue);
                  toHigh.put(low, high);
               }
            } catch (NumberFormatException e) {
               throw new BenchmarkDefinitionException("Cannot parse status range '" + part + "' in '" + entry.getKey() + "'");
            }
         }
      }

      Integer lastLow = null, lastHigh = null;
      T lastValue = null;
      for (Map.Entry<Integer, T> entry : byLow.entrySet()) {
         Integer high = toHigh.get(entry.getKey());
         if (lastValue == entry.getValue() && lastHigh != null && lastHigh == entry.getKey() - 1) {
            lastHigh = high;
            continue;
         }
         if (lastValue != null) {
            ranges.add(lastLow);
            ranges.add(lastHigh);
            values.add(lastValue);
         }
         lastLow = entry.getKey();
         lastHigh = high;
         lastValue = entry.getValue();
      }
      if (lastValue != null) {
         ranges.add(lastLow);
         ranges.add(lastHigh);
         values.add(lastValue);
      }
      return other;
   }
}
