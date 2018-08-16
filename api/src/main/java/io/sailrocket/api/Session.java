package io.sailrocket.api;

import io.netty.buffer.ByteBuf;

public interface Session {
   Object getObject(Object key);

   Session setObject(Object name, Object value);

   int getInt(Object key);

   Session setInt(Object key, int value);

   default Session addToInt(Object key, int delta) {
      setInt(key, getInt(key) + delta);
      return this;
   }

   interface Processor {
      /**
       * Invoked before we record first value from given response.
       * @param session
       */
      default void before(Session session) {
      }

      void process(Session session, ByteBuf data, int offset, int length);

      /**
       * Invoked after we record the last value from given response.
       * @param session
       */
      default void after(Session session) {
      }
   }
}
