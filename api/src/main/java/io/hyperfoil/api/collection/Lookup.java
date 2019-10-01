package io.hyperfoil.api.collection;

import java.lang.reflect.Array;
import java.util.function.Supplier;

/**
 * Primitive hashmap with limited insertion capability. Provides cached arrays with all values.
 */
public class Lookup<K, V> {
   final Class<V> clazz;
   final Supplier<V> factory;
   Object[] keys;
   V[] values;
   V[] array;
   int size;

   public Lookup(Class<V> clazz, Supplier<V> factory) {
      this.clazz = clazz;
      this.factory = factory;
      this.keys = new Object[4];
      this.values = newArray(4);
   }

   public V get(K key) {
      int mask = keys.length - 1;
      int slot = key.hashCode() & mask;
      for (; ; ) {
         Object k2 = keys[slot];
         if (k2 == null) {
            return null;
         } else if (k2.equals(key)) {
            return values[slot];
         }
         slot = (slot + 1) & mask;
      }
   }

   public V reserve(K key) {
      V existing = get(key);
      if (existing != null) {
         return existing;
      }
      // we aim at 50% occupancy at most
      if (++size * 2 > values.length) {
         int newSize = keys.length * 2;
         Object[] newKeys = new Object[newSize];
         V[] newValues = newArray(newSize);
         int mask = newSize - 1;
         for (int i = 0; i < keys.length; ++i) {
            Object k = keys[i];
            if (k != null) {
               insert(newKeys, newValues, mask, k, values[i]);
            }
         }
         keys = newKeys;
         values = newValues;
      }
      V newValue = factory.get();
      insert(keys, values, keys.length - 1, key, newValue);
      array = null;
      return newValue;
   }

   private void insert(Object[] newKeys, V[] newValues, int mask, Object k, V v) {
      int slot = k.hashCode() & mask;
      Object otherKey;
      while ((otherKey = newKeys[slot]) != null) {
         assert !otherKey.equals(k);
         slot = (slot + 1) & mask;
      }
      newKeys[slot] = k;
      newValues[slot] = v;
   }

   @SuppressWarnings("unchecked")
   private V[] newArray(int size) {
      return (V[]) Array.newInstance(clazz, size);
   }

   /**
    * @return Cached map with the values. Should not be modified!
    */
   public V[] array() {
      if (array == null) {
         array = newArray(size);
         int i = 0;
         for (V v : values) {
            if (v != null) {
               array[i++] = v;
            }
         }
      }
      return array;
   }
}
