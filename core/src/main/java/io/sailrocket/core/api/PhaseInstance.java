package io.sailrocket.core.api;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import io.sailrocket.api.ConcurrentPool;
import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Session;

public interface PhaseInstance {
   Phase definition();

   Status status();

   void proceed(HttpClientPool clientPool);

   long absoluteStartTime();

   void start(HttpClientPool clientPool);

   void finish();

   void terminate();

   // TODO better name
   void setComponents(ConcurrentPool<Session> sessions, Lock statusLock, Condition statusCondition);

   void reserveSessions();

   void notifyFinished(Session session);

   void notifyTerminated(Session session);

   void setTerminated();

   void fail(Throwable error);

   Throwable getError();

   enum Status {
      NOT_STARTED,
      RUNNING,
      FINISHED,
      TERMINATING,
      TERMINATED;

      public boolean isFinished() {
         return this.ordinal() >= FINISHED.ordinal();
      }
   }
}
