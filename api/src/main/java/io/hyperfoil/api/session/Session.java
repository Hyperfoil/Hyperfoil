package io.hyperfoil.api.session;

import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.http.ValidatorResults;
import io.hyperfoil.api.config.Phase;

public interface Session {
   /**
    * @return Integer >= 0 that's unique across whole simulation
    */
   int uniqueId();

   /// Common utility objects
   HttpConnectionPool httpConnectionPool(String baseUrl);

   EventExecutor executor();

   SharedData sharedData();

   Phase phase();

   ValidatorResults validatorResults();

   Statistics statistics(int sequenceId);

   Statistics[] statistics();

   /// Variable-related methods
   Session declare(Object key);

   Object getObject(Object key);

   Session setObject(Object key, Object value);

   Session declareInt(Object key);

   int getInt(Object key);

   Session setInt(Object key, int value);

   <V extends Var> V getVar(Object key);

   default Session addToInt(Object key, int delta) {
      setInt(key, getInt(key) + delta);
      return this;
   }

   boolean isSet(Object key);

   /**
    * Make variable set without changing it's (pre-allocated) value.
    */
   Object activate(Object key);

   Session unset(Object key);

   // Resources
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, R resource);

   <R extends Session.Resource> R getResource(ResourceKey<R> key);

   // Sequence related methods
   void currentSequence(SequenceInstance current);

   SequenceInstance currentSequence();

   void attach(EventExecutor executor, SharedData sharedData, Map<String, HttpConnectionPool> httpConnectionPools, Statistics[] statistics);

   void start(PhaseInstance phase);

   /**
    * Run anything that can be executed.
    */
   void proceed();

   void reset();

   void nextSequence(String name);

   void stop();

   void fail(Throwable t);

   boolean isActive();

   LimitedPool<Request> requestPool();

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

   enum VarType {
      OBJECT,
      INTEGER
   }

   interface Var {
      boolean isSet();
      void unset();
      VarType type();

      default int intValue() {
         throw new UnsupportedOperationException();
      }

      default Object objectValue() {
         throw new UnsupportedOperationException();
      }
   }

   /**
    * Just a marker interface.
    */
   interface Resource {
   }

   interface ResourceKey<R extends Resource> {}
}
