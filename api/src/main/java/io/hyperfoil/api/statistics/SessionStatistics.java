package io.hyperfoil.api.statistics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.Session;

/**
 * This instance holds common statistics shared between all {@link Session sessions} (in given phase) driven by the same executor.
 */
public class SessionStatistics {
   private Phase[] phases;
   private int[] stepIds;
   private Map<String, Statistics>[] maps;
   private int size;

   @SuppressWarnings("unchecked")
   public SessionStatistics() {
      phases = new Phase[4];
      stepIds = new int[4];
      maps = new Map[4];
   }

   public Statistics getOrCreate(Phase phase, int stepId, String name, long startTime) {
      for (int i = 0; i < size; ++i) {
         if (stepIds[i] == stepId && phases[i] == phase) {
            Statistics s = maps[i].get(name);
            if (s == null) {
               s = new Statistics(startTime);
               maps[i].put(name, s);
            }
            return s;
         }
      }
      if (size == stepIds.length) {
         phases = Arrays.copyOf(phases, size * 2);
         stepIds = Arrays.copyOf(stepIds, size * 2);
         maps = Arrays.copyOf(maps, size * 2);
      }

      phases[size] = phase;
      stepIds[size] = stepId;
      Statistics s = new Statistics(startTime);
      HashMap<String, Statistics> map = new HashMap<>();
      map.put(name, s);
      maps[size] = map;
      ++size;
      return s;
   }

   public int size() {
      return size;
   }

   public Phase phase(int index) {
      return phases[index];
   }

   public int step(int index) {
      return stepIds[index];
   }

   public Map<String, Statistics> stats(int index) {
      return maps[index];
   }

   public void prune(Phase phase) {
      int lastGood = size - 1;
      while (lastGood >= 0 && phases[lastGood] == phase) {
         lastGood--;
      }
      int lastSize = size;
      for (int i = 0; i < lastSize; ++i) {
         if (phases[i] == phase) {
            if (lastGood > i) {
               phases[i] = phases[lastGood];
               stepIds[i] = stepIds[lastGood];
               maps[i] = maps[lastGood];
               while (lastGood > i && phases[lastGood] == phase) {
                  lastGood--;
               }
            } else {
               phases[i] = null;
               stepIds[i] = 0;
               maps[i] = null;
            }
            --size;
         }
      }
   }

   private class It implements Iterator<Statistics> {
      int i;
      Iterator<Statistics> it;

      @Override
      public boolean hasNext() {
         if (it != null && it.hasNext()) {
            return true;
         } else {
            while (i < maps.length && maps[i] != null) {
               it = maps[i].values().iterator();
               ++i;
               if (it.hasNext()) {
                  return true;
               }
            }
            return false;
         }
      }

      @Override
      public Statistics next() {
         if (it != null && it.hasNext()) {
            return it.next();
         } else {
            while (i < maps.length && maps[i] != null) {
               it = maps[i].values().iterator();
               ++i;
               if (it.hasNext()) {
                  return it.next();
               }
            }
            throw new NoSuchElementException();
         }
      }
   }

}
