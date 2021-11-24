package io.hyperfoil.api.session;

import java.util.function.LongBinaryOperator;

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

   SharedCounter reserveCounter(String key);

   SharedCounter getCounter(String key);

   interface SharedMap {
      void put(Object key, Object value);

      Object get(Object key);

      int size();

      int capacity();

      void clear();
   }

   /**
    * Counter shared by multiple sessions.
    */
   interface SharedCounter {
      /**
       * @return Current value.
       */
      long get();

      /**
       * @param value Number.
       * @return Previous value.
       */
      long set(long value);

      /**
       * @param value Number.
       * @return Sum of previous value and the parameter.
       */
      long apply(LongBinaryOperator operator, long value);
   }
}
