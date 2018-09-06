package io.sailrocket.api;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public abstract class Phase {
   protected final String name;
   protected final Scenario scenario;
   protected final long startTime;
   protected final Collection<Phase> startAfter;
   protected final Collection<Phase> startAfterStrict;
   protected final long duration;
   protected final long maxDuration;
   protected ConcurrentPool<Session> sessions;
   protected Lock statusLock;
   protected Condition statusCondition;
   // Reads are done without locks
   protected volatile Status status = Status.NOT_STARTED;
   protected long absoluteStartTime;
   protected AtomicInteger activeSessions = new AtomicInteger(0);
   private Throwable error;

   protected Phase(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration) {
      this.name = name;
      this.scenario = scenario;
      this.startTime = startTime;
      this.startAfter = startAfter;
      this.startAfterStrict = startAfterStrict;
      this.duration = duration;
      this.maxDuration = maxDuration;
      if (duration < 0) {
         throw new IllegalArgumentException("Duration was not set for phase '" + name + "'");
      }
      if (scenario == null) {
         throw new IllegalArgumentException("Scenario was not set for phase '" + name + "'");
      }
   }

   public String name() {
      return name;
   }

   public Scenario scenario() {
      return scenario;
   }

   /**
    * @return Start time in milliseconds after benchmark start, or negative value if the phase should start immediately
    * after its dependencies ({@link #startAfter()} and {@link #startAfterStrict()} are satisfied.
    */
   public long startTime() {
      return startTime;
   }

   /**
    * Phases that must be finished (not starting any further user sessions) in order to start.
    */
   public Collection<Phase> startAfter() {
      return startAfter;
   }

   /**
    * Phases that must be terminated (not running any user sessions) in order to start.
    */
   public Collection<Phase> startAfterStrict() {
      return startAfterStrict;
   }

   /**
    * @return Duration in milliseconds over which new user sessions should be started.
    */
   public long duration() {
      return duration;
   }

   /**
    * @return Duration in milliseconds over which user sessions can run. After this time no more requests are allowed.
    */
   public long maxDuration() {
      return maxDuration;
   }

   public Status status() {
      return status;
   }

   protected abstract void proceed(HttpClientPool clientPool);

   public long absoluteStartTime() {
      return absoluteStartTime;
   }

   public void start(HttpClientPool clientPool) {
      assert status == Status.NOT_STARTED;
      status = Status.RUNNING;
      absoluteStartTime = System.currentTimeMillis();
      proceed(clientPool);
   }

   public void finish() {
      assert status == Status.RUNNING;
      status = Status.FINISHED;
   }

   public void terminate() {
      status = Status.TERMINATING;
      if (activeSessions.get() == 0) {
         status = Status.TERMINATED;
      }
   }

   // TODO better name
   public void setComponents(ConcurrentPool<Session> sessions, Lock statusLock, Condition statusCondition) {
      this.sessions = sessions;
      this.statusLock = statusLock;
      this.statusCondition = statusCondition;
   }

   public abstract void reserveSessions();

   public void notifyFinished(Session session) {
      if (activeSessions.decrementAndGet() == 0) {
         setTerminated();
      }
   }

   public void notifyTerminated(Session session) {
      if (activeSessions.decrementAndGet() == 0) {
         setTerminated();
      }
   }

   private void setTerminated() {
      statusLock.lock();
      try {
         if (status.isFinished()) {
            status = Status.TERMINATED;
            statusCondition.signal();
         }
      } finally {
         statusLock.unlock();
      }
   }

   public void fail(Throwable error) {
      this.error = error;
      terminate();
   }

   public Throwable getError() {
      return error;
   }

   public enum Status {
      NOT_STARTED,
      RUNNING,
      FINISHED,
      TERMINATING,
      TERMINATED;

      public boolean isFinished() {
         return this.ordinal() >= FINISHED.ordinal();
      }
   }

   public static class AtOnce extends Phase {
      private final int users;

      public AtOnce(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration, int users) {
         super(name, scenario, startTime, startAfter, startAfterStrict, duration, maxDuration);
         this.users = users;
      }

      @Override
      protected void proceed(HttpClientPool clientPool) {
         assert activeSessions.get() == 0;
         activeSessions.set(users);
         for (int i = 0; i < users; ++i) {
            sessions.acquire().proceed();
         }
      }

      @Override
      public void reserveSessions() {
         sessions.reserve(users);
      }
   }

   public static class Always extends Phase {
      private final int users;

      public Always(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration, int users) {
         super(name, scenario, startTime, startAfter, startAfterStrict, duration, maxDuration);
         this.users = users;
      }

      @Override
      protected void proceed(HttpClientPool clientPool) {
         assert activeSessions.get() == 0;
         activeSessions.set(users);
         for (int i = 0; i < users; ++i) {
            sessions.acquire().proceed();
         }
      }

      @Override
      public void reserveSessions() {
         sessions.reserve(users);
      }

      @Override
      public void notifyFinished(Session session) {
         if (status.isFinished()) {
            super.notifyFinished(session);
         } else {
            session.reset();
            session.proceed();
         }
      }
   }

   public static class RampPerSec extends Phase {
      private final int initialUsersPerSec;
      private final int targetUsersPerSec;
      private int startedUsers = 0;

      public RampPerSec(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration, int initialUsersPerSec, int targetUsersPerSec) {
         super(name, scenario, startTime, startAfter, startAfterStrict, duration, maxDuration);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      @Override
      protected void proceed(HttpClientPool clientPool) {
         long now = System.currentTimeMillis();
         long delta = now - startTime;
         int required = (int) (delta * initialUsersPerSec + (targetUsersPerSec - initialUsersPerSec) * delta / duration) / 1000;
         for (int i = required - startedUsers; i > 0; --i) {
            activeSessions.incrementAndGet();
            sessions.acquire().proceed();
         }
         startedUsers = Math.max(startedUsers, required);
         long nextDelta = 1000 * (startedUsers + 1) * duration / (targetUsersPerSec + initialUsersPerSec * (duration - 1));
         clientPool.schedule(() -> proceed(clientPool), nextDelta - delta, TimeUnit.MILLISECONDS);
      }

      @Override
      public void reserveSessions() {
         // TODO
         sessions.reserve(Math.max(initialUsersPerSec, targetUsersPerSec) * 10);
      }

      @Override
      public void notifyFinished(Session session) {
         sessions.release(session);
         super.notifyFinished(session);
      }
   }

   public static class ConstantPerSec extends Phase {
      private final int usersPerSec;
      private int startedUsers = 0;

      public ConstantPerSec(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration, int usersPerSec) {
         super(name, scenario, startTime, startAfter, startAfterStrict, duration, maxDuration);
         this.usersPerSec = usersPerSec;
      }

      @Override
      protected void proceed(HttpClientPool clientPool) {
         long now = System.currentTimeMillis();
         long delta = now - startTime;
         int required = (int) (delta * usersPerSec / 1000);
         for (int i = required - startedUsers; i > 0; --i) {
            activeSessions.incrementAndGet();
            sessions.acquire().proceed();
         }
         startedUsers = Math.max(startedUsers, required);
         long nextDelta = 1000 * (startedUsers + 1) / usersPerSec;
         clientPool.schedule(() -> proceed(clientPool), nextDelta - delta, TimeUnit.MILLISECONDS);
      }

      @Override
      public void reserveSessions() {
         // TODO
         sessions.reserve(usersPerSec * 10);
      }

      @Override
      public void notifyFinished(Session session) {
         sessions.release(session);
         super.notifyFinished(session);
      }
   }

   public static class Sequentially extends Phase {
      private final int repeats;
      private int counter = 0;

      public Sequentially(String name, Scenario scenario, long startTime, Collection<Phase> startAfter, Collection<Phase> startAfterStrict, long duration, long maxDuration, int repeats) {
         super(name, scenario, startTime, startAfter, startAfterStrict, duration, maxDuration);
         this.repeats = repeats;
      }

      @Override
      protected void proceed(HttpClientPool clientPool) {
         activeSessions.incrementAndGet();
         sessions.acquire().proceed();
      }

      @Override
      public void reserveSessions() {
         sessions.reserve(1);
      }

      @Override
      public void notifyFinished(Session session) {
         if (++counter >= repeats) {
            status = Status.TERMINATING;
            super.notifyFinished(session);
         } else {
            session.reset();
            session.proceed();
         }
      }
   }
}
