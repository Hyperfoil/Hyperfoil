package io.hyperfoil.core.impl;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.netty.util.concurrent.EventExecutorGroup;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.PhaseInstance;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public abstract class PhaseInstanceImpl<D extends Phase> implements PhaseInstance {
   protected static final Logger log = LogManager.getLogger(PhaseInstanceImpl.class);
   protected static final boolean trace = log.isTraceEnabled();

   private static final Map<Class<? extends Phase>, PhaseCtor<?>> constructors = new HashMap<>();

   protected final D def;
   private final String runId;
   private final int agentId;
   private final int agentThreads;
   private final int agentFirstThreadId;

   protected ElasticPool<Session> sessionPool;
   protected List<Session> sessionList;
   private PhaseChangeHandler phaseChangeHandler;
   // Reads are done without locks
   protected volatile Status status = Status.NOT_STARTED;
   protected long absoluteStartTime;
   protected AtomicInteger activeSessions = new AtomicInteger(0);
   private volatile Throwable error;
   private volatile boolean sessionLimitExceeded;

   public static PhaseInstance newInstance(Phase def, String runId, int agentId) {
      @SuppressWarnings("unchecked")
      PhaseCtor<Phase> ctor = (PhaseCtor<Phase>) constructors.get(def.getClass());
      if (ctor == null) throw new BenchmarkDefinitionException("Unknown phase type: " + def);
      return ctor.create(def, runId, agentId);
   }

   interface PhaseCtor<P extends Phase> {
      PhaseInstance create(P phase, String runId, int agentId);
   }

   static {
      constructors.put(Phase.AtOnce.class, (PhaseCtor<Phase.AtOnce>) AtOnce::new);
      constructors.put(Phase.Always.class, (PhaseCtor<Phase.Always>) Always::new);
      constructors.put(Phase.RampRate.class, (PhaseCtor<Phase.RampRate>) RampRate::new);
      constructors.put(Phase.ConstantRate.class, (PhaseCtor<Phase.ConstantRate>) ConstantRate::new);
      constructors.put(Phase.Sequentially.class, (PhaseCtor<Phase.Sequentially>) Sequentially::new);
      constructors.put(Phase.Noop.class, (PhaseCtor<Phase.Noop>) Noop::new);
   }

   protected PhaseInstanceImpl(D def, String runId, int agentId) {
      this.def = def;
      this.runId = runId;
      this.agentId = agentId;
      this.agentThreads = def.benchmark().threads(agentId);
      this.agentFirstThreadId = IntStream.range(0, agentId).map(id -> def.benchmark().threads(id)).sum();
   }

   @Override
   public D definition() {
      return def;
   }

   @Override
   public Status status() {
      return status;
   }

   @Override
   public long absoluteStartTime() {
      return absoluteStartTime;
   }

   @Override
   public void start(EventExecutorGroup executorGroup) {
      assert status == Status.NOT_STARTED : "Status is " + status;
      status = Status.RUNNING;
      absoluteStartTime = System.currentTimeMillis();
      log.debug("{} changing status to RUNNING", def.name);
      phaseChangeHandler.onChange(def, Status.RUNNING, false, error).thenRun(() -> proceed(executorGroup));
   }

   @Override
   public void finish() {
      assert status == Status.RUNNING : "Status is " + status;
      status = Status.FINISHED;
      log.debug("{} changing status to FINISHED", def.name);
      BenchmarkExecutionException error = null;
      phaseChangeHandler.onChange(def, Status.FINISHED, sessionLimitExceeded, error);
   }

   @Override
   public void tryTerminate() {
      assert status.isFinished();
      if (activeSessions.compareAndSet(0, Integer.MIN_VALUE)) {
         setTerminated();
      } else if (sessionList != null && status == Status.TERMINATING) {
         // We need to force blocked sessions to check the termination status
         synchronized (sessionList) {
            for (int i = 0; i < sessionList.size(); i++) {
               Session session = sessionList.get(i);
               if (session.isActive()) {
                  session.proceed();
               }
            }
         }
      }
   }

   @Override
   public void terminate() {
      if (status.ordinal() < Status.TERMINATED.ordinal()) {
         status = Status.TERMINATING;
      }
      log.debug("{} changing status to TERMINATING", def.name);
      tryTerminate();
   }

   // TODO better name
   @Override
   public void setComponents(ElasticPool<Session> sessionPool, List<Session> sessionList, PhaseChangeHandler phaseChangeHandler) {
      this.sessionPool = sessionPool;
      this.sessionList = sessionList;
      this.phaseChangeHandler = phaseChangeHandler;
   }

   @Override
   public void notifyFinished(Session session) {
      if (session != null) {
         sessionPool.release(session);
      }
      int numActive = activeSessions.decrementAndGet();
      if (trace) {
         log.trace("#{} NotifyFinished, {} has {} active sessions", session == null ? -1 : session.uniqueId(), def.name, numActive);
      }
      if (numActive < 0) {
         throw new IllegalStateException(def.name + " has " + numActive + " active sessions");
      }
      if (numActive == 0 && status.isFinished() && activeSessions.compareAndSet(0, Integer.MIN_VALUE)) {
         setTerminated();
      }
   }

   @Override
   public void setTerminated() {
      status = Status.TERMINATED;
      log.debug("{} changing status to TERMINATED", def.name);
      phaseChangeHandler.onChange(def, status, false, error);
   }

   @Override
   public void fail(Throwable error) {
      this.error = error;
      terminate();
   }

   @Override
   public void setSessionLimitExceeded() {
      sessionLimitExceeded = true;
   }

   @Override
   public Throwable getError() {
      return error;
   }

   @Override
   public String runId() {
      return runId;
   }

   @Override
   public int agentId() {
      return agentId;
   }

   @Override
   public int agentThreads() {
      return agentThreads;
   }

   @Override
   public int agentFirstThreadId() {
      return agentFirstThreadId;
   }

   @Override
   public void setStatsComplete() {
      // This method is used only for local simulation (in tests)
      if (status != Status.TERMINATED) {
         throw new IllegalStateException();
      }
      log.debug("{} changing status to STATS_COMPLETE", def.name);
      status = Status.STATS_COMPLETE;
   }

   protected boolean startNewSession() {
      int numActive = activeSessions.incrementAndGet();
      if (numActive < 0) {
         // finished
         return true;
      }
      if (trace) {
         log.trace("{} has {} active sessions", def.name, numActive);
      }
      Session session;
      try {
         session = sessionPool.acquire();
      } catch (Throwable t) {
         log.error("Error during session acquisition", t);
         notifyFinished(null);
         return true;
      }
      if (session == null) {
         notifyFinished(null);
         return true;
      }
      session.start(this);
      return false;
   }

   public static class AtOnce extends PhaseInstanceImpl<Phase.AtOnce> {
      private final int users;

      public AtOnce(Phase.AtOnce def, String runId, int agentId) {
         super(def, runId, agentId);
         this.users = def.benchmark().slice(def.users, agentId);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         assert activeSessions.get() == 0;
         for (int i = 0; i < users; ++i) {
            startNewSession();
         }
      }

      @Override
      public void reserveSessions() {
         if (users > 0) {
            sessionPool.reserve(users);
         }
      }
   }

   public static class Always extends PhaseInstanceImpl<Phase.Always> {
      final int users;

      public Always(Phase.Always def, String runId, int agentId) {
         super(def, runId, agentId);
         users = def.benchmark().slice(def.users, agentId);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         assert activeSessions.get() == 0;
         for (int i = 0; i < users; ++i) {
            startNewSession();
         }
      }

      @Override
      public void reserveSessions() {
         if (users > 0) {
            sessionPool.reserve(users);
         }
      }

      @Override
      public void notifyFinished(Session session) {
         if (status.isFinished() || session == null) {
            super.notifyFinished(session);
         } else {
            session.start(this);
         }
      }
   }

   protected abstract static class OpenModelPhase<P extends Phase.OpenModelPhase> extends PhaseInstanceImpl<P> {
      protected final int maxSessions;
      protected final Random random = new Random();
      protected double nextScheduled;
      protected AtomicLong throttledUsers = new AtomicLong(0);
      protected long startedOrThrottledUsers = 0;

      protected OpenModelPhase(P def, String runId, int agentId) {
         super(def, runId, agentId);
         maxSessions = Math.max(1, def.benchmark().slice(def.maxSessions, agentId));
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         if (status.isFinished()) {
            return;
         }
         long now = System.currentTimeMillis();
         long delta = now - absoluteStartTime;
         long nextDelta;

         if (def.variance) {
            while (delta > nextScheduled) {
               if (startNewSession()) {
                  throttledUsers.incrementAndGet();
               }
               startedOrThrottledUsers++;
               // TODO: after many iterations there will be some skew due to imprecise double calculations
               // Maybe we could restart from the expected rate every 1000th session?
               nextScheduled = nextSessionRandomized();
            }
         } else {
            long required = nextSessionMetronome(delta);
            for (long i = required - startedOrThrottledUsers; i > 0; --i) {
               if (startNewSession()) {
                  throttledUsers.addAndGet(i);
                  break;
               }
            }
            startedOrThrottledUsers = Math.max(required, startedOrThrottledUsers);
         }
         nextDelta = (long) Math.ceil(nextScheduled);

         if (trace) {
            log.trace("{}: {} after start, {} started ({} throttled), next user in {} ms", def.name, delta,
                  startedOrThrottledUsers, throttledUsers.get(), nextDelta - delta);
         }
         executorGroup.schedule(() -> proceed(executorGroup), nextDelta - delta, TimeUnit.MILLISECONDS);
      }

      protected abstract long nextSessionMetronome(long delta);

      protected abstract double nextSessionRandomized();

      @Override
      public void reserveSessions() {
         log.debug("Phase {} reserving {} sessions", def.name, maxSessions);
         sessionPool.reserve(maxSessions);
      }

      @Override
      public void notifyFinished(Session session) {
         if (session != null && !status.isFinished()) {
            long throttled = throttledUsers.get();
            while (throttled != 0) {
               if (throttledUsers.compareAndSet(throttled, throttled - 1)) {
                  // TODO: it would be nice to compensate response times
                  // in these invocations for the fact that we're applying
                  // SUT feedback, but that would be imprecise anyway.
                  session.start(this);
                  return;
               } else {
                  throttled = throttledUsers.get();
               }
            }
         }
         super.notifyFinished(session);
      }
   }

   public static class RampRate extends OpenModelPhase<Phase.RampRate> {
      private final double initialUsersPerSec;
      private final double targetUsersPerSec;

      public RampRate(Phase.RampRate def, String runId, int agentId) {
         super(def, runId, agentId);
         initialUsersPerSec = def.benchmark().slice(def.initialUsersPerSec, agentId);
         targetUsersPerSec = def.benchmark().slice(def.targetUsersPerSec, agentId);
         nextScheduled = def.variance ? nextSessionRandomized() : 0;
      }

      @Override
      protected long nextSessionMetronome(long delta) {
         double progress = (targetUsersPerSec - initialUsersPerSec) / (def.duration * 1000);
         long required = (long) (((progress * (delta + 1)) / 2 + initialUsersPerSec / 1000) * delta);
         // Next time is the root of quadratic equation
         double bCoef = progress + initialUsersPerSec / 500;
         nextScheduled = Math.ceil((-bCoef + Math.sqrt(bCoef * bCoef + 8 * progress * (startedOrThrottledUsers + 1))) / (2 * progress));
         return required;
      }

      @Override
      protected double nextSessionRandomized() {
         // we're solving quadratic equation coming from t = (duration * -log(rand))/(((t + now) * (target - initial)) + initial * duration)
         double aCoef = (targetUsersPerSec - initialUsersPerSec);
         if (aCoef < 0.000001) {
            // prevent division 0f/0f
            return 1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / initialUsersPerSec;
         }
         double bCoef = nextScheduled * (targetUsersPerSec - initialUsersPerSec) + initialUsersPerSec * def.duration;
         double cCoef = def.duration * 1000 * Math.log(random.nextDouble());
         return nextScheduled + (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
      }
   }

   public static class ConstantRate extends OpenModelPhase<Phase.ConstantRate> {
      private final double usersPerSec;

      public ConstantRate(Phase.ConstantRate def, String runId, int agentId) {
         super(def, runId, agentId);
         usersPerSec = def.benchmark().slice(def.usersPerSec, agentId);
         nextScheduled = def.variance ? nextSessionRandomized() : 0;
      }

      @Override
      protected long nextSessionMetronome(long delta) {
         long required = (long) (delta * usersPerSec / 1000);
         nextScheduled = (1000 * (startedOrThrottledUsers + 1) + usersPerSec) / usersPerSec;
         return required;
      }

      @Override
      protected double nextSessionRandomized() {
         return nextScheduled + (1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / usersPerSec);
      }
   }

   public static class Sequentially extends PhaseInstanceImpl<Phase.Sequentially> {
      private int counter = 0;

      public Sequentially(Phase.Sequentially def, String runId, int agentId) {
         super(def, runId, agentId);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         assert activeSessions.get() == 0;
         startNewSession();
      }

      @Override
      public void reserveSessions() {
         sessionPool.reserve(1);
      }

      @Override
      public void notifyFinished(Session session) {
         if (++counter >= def.repeats) {
            status = Status.TERMINATING;
            log.debug("{} changing status to TERMINATING", def.name);
            super.notifyFinished(session);
         } else {
            session.start(this);
         }
      }
   }

   public static class Noop extends PhaseInstanceImpl<Phase.Noop> {
      protected Noop(Phase.Noop def, String runId, int agentId) {
         super(def, runId, agentId);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
      }

      @Override
      public void reserveSessions() {
      }
   }
}
