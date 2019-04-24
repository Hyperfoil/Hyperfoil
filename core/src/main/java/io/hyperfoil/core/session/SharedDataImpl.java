package io.hyperfoil.core.session;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.SharedData;

public class SharedDataImpl implements SharedData {
   private final Map<String, SharedMapSet> maps = new HashMap<>();

   @Override
   public void reserveMap(String key, Access match, int entries) {
      SharedMapSet existing = maps.get(key);
      if (existing != null) {
         if (match != null) {
            if (existing instanceof IndexedSharedMapSet) {
               ((IndexedSharedMapSet) existing).ensureIndex(match).ensureEntries(entries);
            } else {
               maps.put(key, new IndexedSharedMapSet(existing, match, entries));
            }
         } else {
            existing.ensureEntries(entries);
         }
      } else {
         if (match != null) {
            maps.put(key, new IndexedSharedMapSet(match, entries));
         } else {
            maps.put(key, new SharedMapSet(entries));
         }
      }
   }

   @Override
   public SharedMap newMap(String key) {
      SharedMapSet set = maps.get(key);
      return set.newMap();
   }

   @Override
   public SharedMap pullMap(String key) {
      return maps.get(key).acquireRandom();
   }

   @Override
   public SharedMap pullMap(String key, Access match, Object value) {
      return maps.get(key).acquireRandom(match, value);
   }

   @Override
   public void pushMap(String key, SharedMap sharedMap) {
      maps.get(key).insert(sharedMap);
   }

   @Override
   public void releaseMap(String key, SharedMap map) {
      map.clear();
      maps.get(key).release(map);
   }

   private static class SharedMapSet {
      MapImpl[] unused;
      int unusedSize;
      int maxEntries;
      MapImpl[] maps;
      int currentSize;

      SharedMapSet(int entries) {
         unused = new MapImpl[16];
         maps = new MapImpl[16];
         maxEntries = entries;
      }

      SharedMapSet(SharedMapSet set, int entries) {
         assert set.currentSize == 0;
         assert set.unusedSize == 0;
         unused = set.unused;
         maps = set.maps;
         maxEntries = Math.max(set.maxEntries, entries);
      }

      void ensureEntries(int entries) {
         if (entries > maxEntries) {
            assert unusedSize == 0;
            maxEntries = entries;
         }
      }

      SharedMap newMap() {
         if (unusedSize == 0) {
            return new MapImpl(maxEntries, numIndices());
         } else {
            SharedMap last = unused[--unusedSize];
            unused[unusedSize] = null;
            return last;
         }
      }

      protected int numIndices() {
         return 0;
      }

      private MapImpl acquireLast() {
         if (currentSize <= 0) {
            return null;
         }
         int pos = --currentSize;
         MapImpl map = maps[pos];
         maps[pos] = null;
         return map;
      }

      public SharedMap acquireRandom(Access match, Object value) {
         throw new UnsupportedOperationException("Cannot match " + match + ": not indexed");
      }

      public SharedMap acquireRandom() {
         if (currentSize == 0) {
            return null;
         }
         int pos = ThreadLocalRandom.current().nextInt(currentSize);
         if (pos == currentSize - 1) {
            return acquireLast();
         } else {
            SharedMap map = maps[pos];
            maps[pos] = acquireLast();
            return map;
         }
      }

      public int insert(SharedMap map) {
         if (currentSize == maps.length) {
            maps = Arrays.copyOf(maps, maps.length * 2);
         }
         int mainIndex = currentSize++;
         assert maps[mainIndex] == null;
         maps[mainIndex] = (MapImpl) map;
         return mainIndex;
      }

      public void release(SharedMap map) {
         if (unusedSize == unused.length) {
            unused = Arrays.copyOf(unused, unused.length * 2);
         }
         unused[unusedSize++] = (MapImpl) map;
      }
   }

   private static class Positions {
      private int[] array = new int[16];
      private int size;

      int insert(int target) {
         if (size == array.length) {
            array = Arrays.copyOf(array, array.length * 2);
         }
         int pos = size++;
         array[pos] = target;
         return pos;
      }

      int moveLastTo(int pos) {
         assert size != 0;
         --size;
         if (size == 0) {
            return -1;
         }
         return array[pos] = array[size];
      }
   }

   private static class IndexedSharedMapSet extends SharedMapSet {
      private Positions[] unusedPositions = new Positions[16];
      private int unusedPositionsSize = 0;
      private Map<Object, Positions>[] positions;
      private Access[] indices;
      private Function<Object, Positions> acquirePosition = ignored -> acquirePosition();

      @SuppressWarnings("unchecked")
      IndexedSharedMapSet(Access index, int entries) {
         super(entries);
         this.indices = new Access[] { index };
         this.positions = new Map[] { new HashMap<>() };
      }

      @SuppressWarnings("unchecked")
      IndexedSharedMapSet(SharedMapSet set, Access index, int entries) {
         super(set, entries);
         this.indices = new Access[] { index };
         this.positions = new Map[] { new HashMap<>() };
      }

      @Override
      protected int numIndices() {
         return indices.length;
      }

      IndexedSharedMapSet ensureIndex(Access index) {
         for (Access i : indices) {
            if (i.equals(index)) return this;
         }
         indices = Arrays.copyOf(indices, indices.length + 1);
         positions = Arrays.copyOf(positions, positions.length + 1);
         indices[indices.length - 1] = index;
         positions[positions.length - 1] = new HashMap<>();
         return this;
      }

      @Override
      public SharedMap acquireRandom() {
         if (currentSize == 0) {
            return null;
         }
         return acquireAt(ThreadLocalRandom.current().nextInt(currentSize));
      }

      @Override
      public SharedMap acquireRandom(Access match, Object value) {
         Positions ps = null;
         for (int i = 0; i < indices.length; ++i) {
            if (indices[i].equals(match)) {
               ps = positions[i].get(value);
               if (ps == null) {
                  return null;
               }
            }
         }
         assert ps != null : "No index for " + match;
         int mainIndex = ps.array[ThreadLocalRandom.current().nextInt(ps.size)];
         return acquireAt(mainIndex);
      }

      private SharedMap acquireAt(int mainIndex) {
         MapImpl map = maps[mainIndex];
         assert map.indexLocations.length == indices.length;
         // remove this map from indices
         for (int i = 0; i < indices.length; ++i) {
            Access index = indices[i];
            Object value2 = map.find(index);
            if (value2 == null) {
               continue;
            }
            Positions ps2 = this.positions[i].get(value2);
            int ps2index = map.indexLocations[i];
            int mainIndexUpdated = ps2.moveLastTo(ps2index);
            if (mainIndexUpdated >= 0) {
               maps[mainIndexUpdated].indexLocations[i] = ps2index;
            } else {
               positions[i].remove(value2);
               releasePositions(ps2);
            }
         }
         --currentSize;
         if (mainIndex != currentSize) {
            // relocate last element (updating its indices)
            MapImpl relocated = maps[mainIndex] = maps[currentSize];
            assert relocated != null;
            for (int i = 0; i < indices.length; ++i) {
               Access index = indices[i];
               Object value3 = relocated.find(index);
               if (value3 == null) {
                  continue;
               }
               Positions ps3 = positions[i].get(value3);
               ps3.array[relocated.indexLocations[i]] = mainIndex;
            }
         } else {
            maps[mainIndex] = null;
         }
         return map;
      }

      @Override
      public int insert(SharedMap map) {
         MapImpl impl = (MapImpl) map;
         int mainIndex = super.insert(map);
         for (int i = 0; i < indices.length; ++i) {
            Object value = map.find(indices[i]);
            impl.indexLocations[i] = positions[i].computeIfAbsent(value, acquirePosition).insert(mainIndex);
         }
         return mainIndex;
      }

      private void releasePositions(Positions ps) {
         if (unusedPositionsSize == unusedPositions.length) {
            unusedPositions = Arrays.copyOf(unusedPositions, unusedPositions.length * 2);
         }
         unusedPositions[unusedPositionsSize++] = ps;
      }

      private Positions acquirePosition() {
         if (unusedPositionsSize == 0) {
            return new Positions();
         }
         return unusedPositions[--unusedPositionsSize];
      }
   }

   private static class MapImpl implements SharedMap {
      int[] indexLocations;
      Access[] keys;
      Object[] values;
      int size;

      MapImpl(int capacity, int indices) {
         indexLocations = indices > 0 ? new int[indices] : null;
         keys = new Access[capacity];
         values = new Object[capacity];
      }

      @Override
      public void put(Access key, Object value) {
         int pos = size++;
         keys[pos] = key;
         values[pos] = value;
      }

      @Override
      public int size() {
         return size;
      }

      @Override
      public Access key(int i) {
         return keys[i];
      }

      @Override
      public Object value(int i) {
         return values[i];
      }

      @Override
      public int capacity() {
         return keys.length;
      }

      @Override
      public void clear() {
         for (int i = size - 1; i >= 0; --i) {
            keys[i] = null;
            values[i] = null;
         }
         size = 0;
      }

      @Override
      public Object find(Access index) {
         for (int i = 0; i < size; ++i) {
            if (keys[i].equals(index)) {
               return values[i];
            }
         }
         return null;
      }
   }
}
