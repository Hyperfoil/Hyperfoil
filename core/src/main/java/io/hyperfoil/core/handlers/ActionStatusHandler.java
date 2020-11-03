package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.util.Util;

public class ActionStatusHandler implements StatusHandler {
   private final int[] statusRanges;
   private final Action[][] actions;

   public ActionStatusHandler(int[] statusRanges, Action[][] actions) {
      assert statusRanges.length == 2 * actions.length;
      this.statusRanges = statusRanges;
      this.actions = actions;
   }

   @Override
   public void handleStatus(Request request, int status) {
      for (int i = 0; i < actions.length; ++i) {
         if (status >= statusRanges[2 * i] && status <= statusRanges[2 * i + 1]) {
            for (Action a : actions[i]) {
               a.run(request.session);
            }
            break;
         }
      }
   }

   @MetaInfServices(StatusHandler.Builder.class)
   @Name("action")
   public static class Builder implements StatusHandler.Builder, PartialBuilder {
      private final Map<String, List<Action.Builder>> actions = new HashMap<>();

      @Override
      public ServiceLoadedBuilderProvider<Action.Builder> withKey(String range) {
         List<Action.Builder> actions = new ArrayList<>();
         this.actions.put(range, actions);
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, actions::add);
      }

      @Override
      public StatusHandler build() {
         TreeMap<Integer, Action[]> byLow = new TreeMap<>();
         Map<Integer, Integer> toHigh = new HashMap<>();
         for (Map.Entry<String, List<Action.Builder>> entry : actions.entrySet()) {
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
                  Action[] actions = entry.getValue().stream().map(Action.Builder::build).toArray(Action[]::new);
                  Integer floor = byLow.floorKey(low);
                  if (floor == null) {
                     Integer ceiling = byLow.ceilingKey(low);
                     if (ceiling != null && ceiling <= high) {
                        throw new BenchmarkDefinitionException("Overlapping ranges: " + low + "-" + high + " and " + ceiling + "-" + toHigh.get(ceiling));
                     }
                     byLow.put(low, actions);
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
                     byLow.put(low, actions);
                     toHigh.put(low, high);
                  }
               } catch (NumberFormatException e) {
                  throw new BenchmarkDefinitionException("Cannot parse status range '" + part + "' in '" + entry.getKey() + "'");
               }
            }
         }

         List<Integer> ranges = new ArrayList<>();
         List<Action[]> actions = new ArrayList<>();

         Integer lastLow = null, lastHigh = null;
         Action[] lastActions = null;
         for (Map.Entry<Integer, Action[]> entry : byLow.entrySet()) {
            Integer high = toHigh.get(entry.getKey());
            if (lastActions == entry.getValue() && lastHigh != null && lastHigh == entry.getKey() - 1) {
               lastHigh = high;
               continue;
            }
            if (lastActions != null) {
               ranges.add(lastLow);
               ranges.add(lastHigh);
               actions.add(lastActions);
            }
            lastLow = entry.getKey();
            lastHigh = high;
            lastActions = entry.getValue();
         }
         if (lastActions != null) {
            ranges.add(lastLow);
            ranges.add(lastHigh);
            actions.add(lastActions);
         }
         return new ActionStatusHandler(ranges.stream().mapToInt(Integer::intValue).toArray(), actions.toArray(new Action[0][]));
      }
   }
}
