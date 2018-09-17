package io.sailrocket.core.api;

import java.util.function.BiConsumer;

import io.netty.util.concurrent.EventExecutorGroup;
import io.sailrocket.api.ConcurrentPool;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Session;

public interface PhaseInstance {
   Phase definition();

   Status status();

   void proceed(EventExecutorGroup executorGroup);

   long absoluteStartTime();

   void start(EventExecutorGroup executorGroup);

   void finish();

   void terminate();

   // TODO better name
   void setComponents(ConcurrentPool<Session> sessions, BiConsumer<String, Status> phaseChangeHandler);

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
