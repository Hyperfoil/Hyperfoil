package io.hyperfoil.core.impl;

import io.hyperfoil.api.config.Model;
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

public abstract class PhaseInstanceImpl implements PhaseInstance {
   protected static final Logger log = LogManager.getLogger(PhaseInstanceImpl.class);
   protected static final boolean trace = log.isTraceEnabled();

   private static final Map<Class<? extends Model>, PhaseCtor> constructors = new HashMap<>();

   protected final Phase def;
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
   protected String absoluteStartTimeString;
   protected AtomicInteger activeSessions = new AtomicInteger(0);
   private volatile Throwable error;
   private volatile boolean sessionLimitExceeded;

   public static PhaseInstance newInstance(Phase def, String runId, int agentId) {
      PhaseCtor ctor = constructors.get(def.model.getClass());
      if (ctor == null) throw new BenchmarkDefinitionException("Unknown phase type: " + def.model);
      return ctor.create(def, runId, agentId);
   }

   interface PhaseCtor {
      PhaseInstance create(Phase phase, String runId, int agentId);
   }

   static {
      constructors.put(Model.AtOnce.class, AtOnce::new);
      constructors.put(Model.Always.class, Always::new);
      constructors.put(Model.RampRate.class, RampRate::new);
      constructors.put(Model.ConstantRate.class, ConstantRate::new);
      constructors.put(Model.Sequentially.class, Sequentially::new);
      constructors.put(Model.Noop.class, Noop::new);
   }

   protected PhaseInstanceImpl(Phase def, String runId, int agentId) {
      this.def = def;
      this.runId = runId;
      this.agentId = agentId;
      this.agentThreads = def.benchmark().threads(agentId);
      this.agentFirstThreadId = IntStream.range(0, agentId).map(id -> def.benchmark().threads(id)).sum();
   }

   @Override
   public Phase definition() {
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
   public String absoluteStartTimeAsString() {
      return absoluteStartTimeString;
   }

   @Override
   public void start(EventExecutorGroup executorGroup) {
      synchronized (this) {
         assert status == Status.NOT_STARTED : "Status is " + status;
         status = Status.RUNNING;
      }
      absoluteStartTime = System.currentTimeMillis();
      absoluteStartTimeString = String.valueOf(absoluteStartTime);
      log.debug("{} changing status to RUNNING", def.name);
      phaseChangeHandler.onChange(def, Status.RUNNING, false, error).thenRun(() -> proceed(executorGroup));
   }

   @Override
   public void finish() {
      synchronized (this) {
         if (status == Status.RUNNING) {
            status = Status.FINISHED;
            log.debug("{} changing status to FINISHED", def.name);
         } else {
            log.debug("{} already in state {}, not finishing", def.name, status);
         }
      }
      phaseChangeHandler.onChange(def, Status.FINISHED, sessionLimitExceeded, null);
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
      synchronized (this) {
         if (status.ordinal() < Status.TERMINATED.ordinal()) {
            status = Status.TERMINATING;
         }
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
      synchronized (this) {
         status = Status.TERMINATED;
      }
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
      synchronized (this) {
         if (status != Status.TERMINATED) {
            throw new IllegalStateException();
         }
         status = Status.STATS_COMPLETE;
      }
      log.debug("{} changing status to STATS_COMPLETE", def.name);
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

   public static class AtOnce extends PhaseInstanceImpl {
      private final int users;

      public AtOnce(Phase def, String runId, int agentId) {
         super(def, runId, agentId);
         Model.AtOnce model = (Model.AtOnce) def.model;
         if (model.users > 0) {
            this.users = def.benchmark().slice(model.users, agentId);
         } else if (model.usersPerAgent > 0) {
            this.users = model.usersPerAgent;
         } else if (model.usersPerThread > 0) {
            this.users = model.usersPerThread * def.benchmark().threads(agentId);
         } else {
            this.users = 0;
         }
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

   public static class Always extends PhaseInstanceImpl {
      final int users;

      public Always(Phase def, String runId, int agentId) {
         super(def, runId, agentId);
         Model.Always model = (Model.Always) def.model;
         if (model.users > 0) {
            users = def.benchmark().slice(model.users, agentId);
         } else if (model.usersPerAgent > 0) {
            users = model.usersPerAgent;
         } else if (model.usersPerThread > 0) {
            users = model.usersPerThread * def.benchmark().threads(agentId);
         } else {
            users = 0;
         }
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

   protected abstract static class OpenModelPhase extends PhaseInstanceImpl {
      protected final int maxSessions;
      protected final Random random = new Random();
      protected double nextScheduled;
      protected AtomicLong throttledUsers = new AtomicLong(0);
      protected long startedOrThrottledUsers = 0;

      protected OpenModelPhase(Phase def, String runId, int agentId) {
         super(def, runId, agentId);
         maxSessions = Math.max(1, def.benchmark().slice(((Model.OpenModel) def.model).maxSessions, agentId));
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         if (status.isFinished()) {
            return;
         }
         long now = System.currentTimeMillis();
         long delta = now - absoluteStartTime;
         long nextDelta;
         Model.OpenModel model = (Model.OpenModel) def.model;

         if (model.variance) {
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

   public static class RampRate extends OpenModelPhase {
      private final double initialUsersPerSec;
      private final double targetUsersPerSec;

      public RampRate(Phase def, String runId, int agentId) {
         super(def, runId, agentId);
         Model.RampRate model = (Model.RampRate) def.model;
         initialUsersPerSec = def.benchmark().slice(model.initialUsersPerSec, agentId);
         targetUsersPerSec = def.benchmark().slice(model.targetUsersPerSec, agentId);
         nextScheduled = model.variance ? nextSessionRandomized() : 0;
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
         if (Math.abs(aCoef) < 0.000001) {
            // prevent division 0f/0f
            return nextScheduled + 1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / initialUsersPerSec;
         }
         double bCoef = nextScheduled * (targetUsersPerSec - initialUsersPerSec) + initialUsersPerSec * def.duration;
         double cCoef = def.duration * 1000 * Math.log(random.nextDouble());
         return nextScheduled + (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
      }
   }

   public static class ConstantRate extends OpenModelPhase {
      private final double usersPerSec;

      public ConstantRate(Phase def, String runId, int agentId) {
         super(def, runId, agentId);
         Model.ConstantRate model = (Model.ConstantRate) def.model;
         usersPerSec = def.benchmark().slice(model.usersPerSec, agentId);
         nextScheduled = model.variance ? nextSessionRandomized() : 0;
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

   public static class Sequentially extends PhaseInstanceImpl {
      private int counter = 0;

      public Sequentially(Phase def, String runId, int agentId) {
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
         Model.Sequentially model = (Model.Sequentially) def.model;
         if (++counter >= model.repeats) {
            synchronized (this) {
               if (status.ordinal() < Status.TERMINATING.ordinal()) {
                  status = Status.TERMINATING;
                  log.debug("{} changing status to TERMINATING", def.name);
               } else {
                  log.warn("{} not terminating because it is already ", def.name, status);
               }
            }
            super.notifyFinished(session);
         } else {
            session.start(this);
         }
      }
   }

   public static class Noop extends PhaseInstanceImpl {
      protected Noop(Phase def, String runId, int agentId) {
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
