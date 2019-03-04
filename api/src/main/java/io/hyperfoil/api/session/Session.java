package io.hyperfoil.api.session;

import java.util.Map;

import io.hyperfoil.api.config.Scenario;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.http.ValidatorResults;
import io.hyperfoil.api.config.Phase;

public interface Session {

   void reserve(Scenario scenario);

   /**
    * @return Integer >= 0 that's unique across whole simulation
    */
   int uniqueId();

   /// Common utility objects
   HttpConnectionPool httpConnectionPool(String baseUrl);

   String findBaseUrl(String path);

   EventExecutor executor();

   SharedData sharedData();

   Phase phase();

   ValidatorResults validatorResults();

   Statistics statistics(String name);

   /// Variable-related methods
   Session declare(Object key);

   Object getObject(Object key);

   Session setObject(Object key, Object value);

   Session declareInt(Object key);

   int getInt(Object key);

   Session setInt(Object key, int value);

   <V extends Var> V getVar(Object key);

   <V extends Var> V getSequenceScopedVar(Object key);

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

   void attach(EventExecutor executor, SharedData sharedData, Map<String, HttpConnectionPool> httpConnectionPools, Map<String, Statistics> statistics);

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
