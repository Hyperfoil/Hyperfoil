package io.hyperfoil.api.session;

import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.connection.HttpDestinationTable;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpCache;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.config.Phase;

public interface Session {

   void reserve(Scenario scenario);

   /**
    * @return int &gt;= 0 that's unique across whole simulation
    */
   int uniqueId();

   int agentThreadId();

   int agentThreads();

   int globalThreadId();

   int globalThreads();

   int agentId();

   /// Common utility objects
   HttpConnectionPool httpConnectionPool(String authority);

   HttpDestinationTable httpDestinations();

   EventExecutor executor();

   SharedData sharedData();

   Phase phase();

   Statistics statistics(int stepId, String name);

   void pruneStats(Phase phase);

   // Resources
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, R resource);

   <R extends Session.Resource> R getResource(ResourceKey<R> key);

   // Sequence related methods
   void currentSequence(SequenceInstance current);

   SequenceInstance currentSequence();

   void attach(EventExecutor executor, SharedData sharedData, HttpDestinationTable httpDestinations, SessionStatistics statistics);

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

   LimitedPool<HttpRequest> httpRequestPool();

   HttpCache httpCache();

   SequenceInstance acquireSequence();

   void enableSequence(SequenceInstance instance);

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
