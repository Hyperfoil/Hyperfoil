package io.hyperfoil.api.session;

/**
 * Data shared among sessions bound to the same {@link Session#executor() executor thread}.
 */
public interface SharedData {
   SharedMap newMap(String key);

   SharedMap pullMap(String key);

   SharedMap pullMap(String key, WriteAccess match, Object value);

   void pushMap(String key, SharedMap sharedMap);

   void releaseMap(String key, SharedMap map);

   void reserveMap(String key, WriteAccess match, int entries);

   interface SharedMap {
      void put(WriteAccess key, Object value);

      int size();

      WriteAccess key(int i);

      Object value(int i);

      int capacity();

      void clear();

      Object find(WriteAccess index);
   }
}
