package io.hyperfoil.api.session;

/**
 * Data shared among sessions bound to the same {@link Session#executor() executor thread}.
 */
public interface SharedData {

   SharedMap newMap(String key);
   SharedMap pullMap(String key);
   SharedMap pullMap(String key, String match, Object value);
   void pushMap(String key, SharedMap sharedMap);
   void releaseMap(String key, SharedMap map);

   void reserveMap(String key, String match, int entries);

   interface SharedMap {
      void put(String var, Object value);

      int size();

      String key(int i);

      Object value(int i);

      int capacity();

      void clear();

      Object find(String index);
   }
}
