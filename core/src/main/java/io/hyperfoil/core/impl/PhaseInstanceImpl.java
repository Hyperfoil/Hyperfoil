package io.hyperfoil.core.impl;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.netty.util.concurrent.EventExecutorGroup;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.PhaseInstance;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public abstract class PhaseInstanceImpl<D extends Phase> implements PhaseInstance {
   protected static final Logger log = LoggerFactory.getLogger(PhaseInstanceImpl.class);
   protected static final boolean trace = log.isTraceEnabled();

   private static Map<Class<? extends Phase>, Function<? extends Phase, PhaseInstance>> constructors = new HashMap<>();

   protected D def;
   protected ElasticPool<Session> sessionPool;
   protected List<Session> sessionList;
   private PhaseChangeHandler phaseChangeHandler;
   // Reads are done without locks
   protected volatile Status status = Status.NOT_STARTED;
   protected long absoluteStartTime;
   protected AtomicInteger activeSessions = new AtomicInteger(0);
   private volatile Throwable error;
   private volatile boolean sessionLimitExceeded;

   public static PhaseInstance newInstance(Phase def) {
      @SuppressWarnings("unchecked")
      Function<Phase, PhaseInstance> ctor = (Function<Phase, PhaseInstance>) constructors.get(def.getClass());
      if (ctor == null) throw new BenchmarkDefinitionException("Unknown phase type: " + def);
      return ctor.apply(def);
   }

   static {
      constructors.put(Phase.AtOnce.class, (Function<Phase.AtOnce, PhaseInstance>) AtOnce::new);
      constructors.put(Phase.Always.class, (Function<Phase.Always, PhaseInstance>) Always::new);
      constructors.put(Phase.RampPerSec.class, (Function<Phase.RampPerSec, PhaseInstance>) RampPerSec::new);
      constructors.put(Phase.ConstantPerSec.class, (Function<Phase.ConstantPerSec, PhaseInstance>) ConstantPerSec::new);
      constructors.put(Phase.Sequentially.class, (Function<Phase.Sequentially, PhaseInstance>) Sequentially::new);
      constructors.put(Phase.Noop.class, (Function<Phase.Noop, PhaseInstance>) Noop::new);
   }

   protected PhaseInstanceImpl(D def) {
      this.def = def;
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
      phaseChangeHandler.onChange(def, Status.RUNNING, false, error);
      proceed(executorGroup);
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
      if (status != Status.TERMINATED) {
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
      if (numActive < 0)
         log.error("{} has {} active sessions", def.name, numActive);
      if (numActive == 0 && status.isFinished() && activeSessions.compareAndSet(0, Integer.MIN_VALUE)) {
         setTerminated();
      }
   }

   @Override
   public void notifyTerminated(Session session) {
      int numActive = activeSessions.decrementAndGet();
      if (trace) {
         log.trace("{} has {} active sessions", def.name, numActive);
      }
      if (numActive < 0)
         log.error("{} has {} active sessions", def.name, numActive);
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
      public AtOnce(Phase.AtOnce def) {
         super(def);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         assert activeSessions.get() == 0;
         for (int i = 0; i < def.users; ++i) {
            startNewSession();
         }
      }

      @Override
      public void reserveSessions() {
         sessionPool.reserve(def.users);
      }
   }

   public static class Always extends PhaseInstanceImpl<Phase.Always> {
      public Always(Phase.Always def) {
         super(def);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
         assert activeSessions.get() == 0;
         for (int i = 0; i < def.users; ++i) {
            startNewSession();
         }
      }

      @Override
      public void reserveSessions() {
         sessionPool.reserve(def.users);
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
      protected final Random random = new Random();
      protected double nextScheduled = nextSessionRandomized();
      protected AtomicLong throttledUsers = new AtomicLong(0);
      protected long startedOrThrottledUsers = 0;

      protected OpenModelPhase(P def) {
         super(def);
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
         sessionPool.reserve(def.maxSessions);
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

   public static class RampPerSec extends OpenModelPhase<Phase.RampPerSec> {

      public RampPerSec(Phase.RampPerSec def) {
         super(def);
      }

      @Override
      protected long nextSessionMetronome(long delta) {
         double progress = (def.targetUsersPerSec - def.initialUsersPerSec) / (def.duration * 1000);
         long required = (long) (((progress * (delta + 1)) / 2 + def.initialUsersPerSec / 1000) * delta);
         // Next time is the root of quadratic equation
         double bCoef = progress + def.initialUsersPerSec / 500;
         nextScheduled = Math.ceil((-bCoef + Math.sqrt(bCoef * bCoef + 8 * progress * (startedOrThrottledUsers + 1))) / (2 * progress));
         return required;
      }

      @Override
      protected double nextSessionRandomized() {
         // we're solving quadratic equation coming from t = (duration * -log(rand))/(((t + now) * (target - initial)) + initial * duration)
         double aCoef = (def.targetUsersPerSec - def.initialUsersPerSec);
         if (aCoef < 0.000001) {
            // prevent division 0f/0f
            return 1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / def.initialUsersPerSec;
         }
         double bCoef = nextScheduled * (def.targetUsersPerSec - def.initialUsersPerSec) + def.initialUsersPerSec * def.duration;
         double cCoef = def.duration * 1000 * Math.log(random.nextDouble());
         return nextScheduled + (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
      }
   }

   public static class ConstantPerSec extends OpenModelPhase<Phase.ConstantPerSec> {

      public ConstantPerSec(Phase.ConstantPerSec def) {
         super(def);
      }

      @Override
      protected long nextSessionMetronome(long delta) {
         long required = (long) (delta * def.usersPerSec / 1000);
         nextScheduled = (1000 * (startedOrThrottledUsers + 1) + def.usersPerSec) / def.usersPerSec;
         return required;
      }

      @Override
      protected double nextSessionRandomized() {
         return nextScheduled + (1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / def.usersPerSec);
      }
   }

   public static class Sequentially extends PhaseInstanceImpl<Phase.Sequentially> {
      private int counter = 0;

      public Sequentially(Phase.Sequentially def) {
         super(def);
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
      protected Noop(Phase.Noop def) {
         super(def);
      }

      @Override
      public void proceed(EventExecutorGroup executorGroup) {
      }

      @Override
      public void reserveSessions() {
      }
   }
}
