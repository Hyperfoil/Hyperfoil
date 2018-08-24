package io.sailrocket.api;

import io.netty.buffer.ByteBuf;

public interface Session {
   /// Common utility objects
   HttpClientPool httpClientPool();

   ValidatorResults validatorResults();

   Statistics statistics(int sequenceId);

   Statistics[] statistics();

   RequestQueue requestQueue();

   /// Variable-related methods
   Session declare(Object key);

   Object getObject(Object key);

   Session setObject(Object key, Object value);

   Session declareInt(Object key);

   int getInt(Object key);

   Session setInt(Object key, int value);

   default Session addToInt(Object key, int delta) {
      setInt(key, getInt(key) + delta);
      return this;
   }

   boolean isSet(Object key);

   Object activate(Object key);

   void deactivate(Object key);

   // Resources
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, R resource);

   <R extends Session.Resource> R getResource(ResourceKey<R> key);

   // Sequence related methods
   void currentSequence(SequenceInstance current);

   SequenceInstance currentSequence();

   /**
    * Run anything that can be executed.
    */
   void proceed();

   void reset();

   interface Processor {
      /**
       * Invoked before we record first value from given response.
       * @param session
       */
      default void before(Session session) {
      }

      void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart);

      /**
       * Invoked after we record the last value from given response.
       * @param session
       */
      default void after(Session session) {
      }
   }

   interface Var {
      boolean isSet();
      void unset();
   }

   /**
    * Just a marker interface.
    */
   interface Resource {
   }

   interface ResourceKey<R extends Resource> {}
}
