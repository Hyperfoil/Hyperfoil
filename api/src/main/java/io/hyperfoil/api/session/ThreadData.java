package io.hyperfoil.api.session;

/**
 * Data shared among sessions bound to the same {@link Session#executor() executor thread}.
 */
public interface ThreadData {
   SharedMap newMap(String key);

   SharedMap pullMap(String key);

   SharedMap pullMap(String key, Object match, Object value);

   void pushMap(String key, SharedMap sharedMap);

   void releaseMap(String key, SharedMap map);

   void reserveMap(String key, Object match, int entries);

   interface SharedMap {
      void put(Object key, Object value);

      Object get(Object key);

      int size();

      int capacity();

      void clear();
   }
}
