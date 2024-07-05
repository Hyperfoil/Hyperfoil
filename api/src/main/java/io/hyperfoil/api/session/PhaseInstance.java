package io.hyperfoil.api.session;

import java.util.List;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.config.Phase;
import io.netty.util.concurrent.EventExecutorGroup;

public interface PhaseInstance {
   Phase definition();

   Status status();

   void proceed(EventExecutorGroup executorGroup);

   long absoluteStartTime();

   String absoluteStartTimeAsString();

   void start(EventExecutorGroup executorGroup);

   void finish();

   void tryTerminate();

   void terminate();

   // TODO better name
   void setComponents(ElasticPool<Session> sessionPool, List<Session> sessionList, PhaseChangeHandler phaseChangeHandler);

   void runOnFailedSessionAcquisition(Runnable action);

   void reserveSessions();

   void notifyFinished(Session session);

   void setTerminated();

   void fail(Throwable error);

   Throwable getError();

   String runId();

   int agentId();

   int agentThreads();

   int agentFirstThreadId();

   void setStatsComplete();

   enum Status {
      NOT_STARTED,
      RUNNING,
      FINISHED,
      TERMINATING,
      TERMINATED,
      STATS_COMPLETE;

      public boolean isFinished() {
         return this.ordinal() >= FINISHED.ordinal();
      }

      public boolean isTerminated() {
         return this.ordinal() >= TERMINATED.ordinal();
      }

      public boolean isStarted() {
         return this.ordinal() >= RUNNING.ordinal();
      }
   }
}
