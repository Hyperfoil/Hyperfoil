package io.hyperfoil.api.session;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.connection.HttpDestinationTable;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HttpCache;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.config.Phase;

public interface Session extends Callable<Void> {

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

   long phaseStartTimestamp();

   Statistics statistics(int stepId, String name);

   void pruneStats(Phase phase);

   // Resources

   /**
    * See {@link #declareResource(ResourceKey, Supplier, boolean)}, with <code>singleton</code> defaulting to <code>false</code>
    *
    * @param key              Unique key (usually the step or handler itself)
    * @param resourceSupplier Supplier creating the resource, possible multiple times.
    * @param <R>              Resource type.
    */
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier);

   /**
    * Reserve space in the session for a resource, stored under given key. If this is executed within
    * a {@link Sequence sequence} with non-zero {@link Sequence#concurrency() concurrency} the session
    * stores one resource for each concurrent instance. If this behaviour should be avoided set
    * <code>singleton</code> to true.
    *
    * @param key              Unique key (usually the step or handler itself)
    * @param resourceSupplier Supplier creating the resource, possible multiple times.
    * @param singleton        Is the resource shared amongst concurrent sequences?
    * @param <R>              Resource type.
    */
   <R extends Session.Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier, boolean singleton);

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

   SequenceInstance startSequence(String name, boolean forceSameIndex, ConcurrencyPolicy policy);

   void stop();

   void fail(Throwable t);

   boolean isActive();

   LimitedPool<HttpRequest> httpRequestPool();

   HttpCache httpCache();

   /**
    * @return Currently executed request, or <code>null</code> if not in scope.
    */
   Request currentRequest();

   void currentRequest(Request request);

   enum VarType {
      OBJECT,
      INTEGER
   }

   interface Var {
      boolean isSet();

      void unset();

      VarType type();

      // While the session parameter is not necessary for regular Vars stored
      // inside the session it is useful for the special ones.
      default int intValue(Session session) {
         throw new UnsupportedOperationException();
      }

      default Object objectValue(Session session) {
         throw new UnsupportedOperationException();
      }
   }

   interface Resource {
      default void onSessionReset(Session session) {}
   }

   interface ResourceKey<R extends Resource> extends Serializable {}

   /**
    * Behaviour when a new sequence start is requested but the concurrency factor is exceeded.
    */
   enum ConcurrencyPolicy {
      FAIL,
      WARN,
   }
}
