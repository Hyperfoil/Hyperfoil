package io.hyperfoil.core.impl;

import io.netty.util.concurrent.EventExecutorGroup;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.session.PhaseInstance;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public abstract class PhaseInstanceImpl<D extends Phase> implements PhaseInstance {
   protected static final Logger log = LoggerFactory.getLogger(PhaseInstanceImpl.class);
   protected static final boolean trace = log.isTraceEnabled();

   private static Map<Class<? extends Phase>, Function<? extends Phase, PhaseInstance>> constructors = new HashMap<>();

   protected D def;
   protected ElasticPool<Session> sessionPool;
   protected List<Session> sessionList;
   private Iterable<Statistics> statistics;
   private PhaseChangeHandler phaseChangeHandler;
   // Reads are done without locks
   protected volatile Status status = Status.NOT_STARTED;
   protected long absoluteStartTime;
   protected AtomicInteger activeSessions = new AtomicInteger(0);
   private Throwable error;

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
      long now = System.currentTimeMillis();
      for (Statistics stats : statistics) {
         stats.start(now);
      }

      assert status == Status.NOT_STARTED : "Status is " + status;
      status = Status.RUNNING;
      absoluteStartTime = now;
      log.debug("{} changing status to RUNNING", def.name);
      phaseChangeHandler.onChange(def, Status.RUNNING, true);
      proceed(executorGroup);
   }

   @Override
   public void finish() {
      assert status == Status.RUNNING : "Status is " + status;
      status = Status.FINISHED;
      log.debug("{} changing status to FINISHED", def.name);
      int active = activeSessions.get();
      boolean successful = active <= def.maxUnfinishedSessions;
      if (!successful) {
         log.info("Phase {} had {} active sessions, maximum is {}", def.name, active, def.maxUnfinishedSessions);
      }
      phaseChangeHandler.onChange(def, Status.FINISHED, successful);
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
   public void setComponents(ElasticPool<Session> sessionPool, List<Session> sessionList, Iterable<Statistics> statistics, PhaseChangeHandler phaseChangeHandler) {
      this.sessionPool = sessionPool;
      this.sessionList = sessionList;
      this.statistics = statistics;
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
      long now = System.currentTimeMillis();
      for (Statistics stats : statistics) {
         stats.end(now);
      }
      phaseChangeHandler.onChange(def, status, true);
   }

   @Override
   public void fail(Throwable error) {
      this.error = error;
      terminate();
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
      Session session = sessionPool.acquire();
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
         activeSessions.set(def.users);
         for (int i = 0; i < def.users; ++i) {
            Session session = sessionPool.acquire();
            if (session != null) {
               session.start(this);
            } else {
               notifyFinished(null);
            }
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
         activeSessions.set(def.users);
         for (int i = 0; i < def.users; ++i) {
            Session session = sessionPool.acquire();
            if (session != null) {
               session.start(this);
            } else {
               notifyFinished(null);
            }
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

   public static class RampPerSec extends PhaseInstanceImpl<Phase.RampPerSec> {
      private final Random random = new Random();
      private int startedUsers = 0;
      private double nextScheduled = nextSession();

      public RampPerSec(Phase.RampPerSec def) {
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
                  return;
               }
               ++startedUsers;
               // TODO: after many iterations there will be some skew due to imprecise double calculations
               // Maybe we could restart from the expected rate every 1000th session?
               nextScheduled += nextSession();
            }
            nextDelta = (long) Math.ceil(nextScheduled);
         } else {
            double progress = (def.targetUsersPerSec - def.initialUsersPerSec) / (def.duration * 1000);
            int required = (int) (((progress * (delta + 1)) / 2 + def.initialUsersPerSec / 1000) * delta);
            for (int i = required - startedUsers; i > 0; --i) {
               if (startNewSession()) {
                  return;
               }
            }
            startedUsers = Math.max(startedUsers, required);
            // Next time is the root of quadratic equation
            double bCoef = progress + def.initialUsersPerSec / 500;
            nextDelta = (long) Math.ceil((-bCoef + Math.sqrt(bCoef * bCoef + 8 * progress * (startedUsers + 1))) / (2 * progress));
         }
         if (trace) {
            log.trace("{}: {} after start, {} started, next user in {} ms", def.name, delta, startedUsers, nextDelta - delta);
         }
         executorGroup.schedule(() -> proceed(executorGroup), nextDelta - delta, TimeUnit.MILLISECONDS);
      }

      private double nextSession() {
         // we're solving quadratic equation coming from t = (duration * -log(rand))/(((t + now) * (target - initial)) + initial * duration)
         double aCoef = (def.targetUsersPerSec - def.initialUsersPerSec);
         double bCoef = nextScheduled * (def.targetUsersPerSec - def.initialUsersPerSec) + def.initialUsersPerSec * def.duration;
         double cCoef = def.duration * 1000 * Math.log(random.nextDouble());
         return (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
      }

      @Override
      public void reserveSessions() {
         sessionPool.reserve(def.maxSessionsEstimate);
      }
   }

   public static class ConstantPerSec extends PhaseInstanceImpl<Phase.ConstantPerSec> {
      private final Random random = new Random();
      private int startedUsers = 0;
      private double nextScheduled = nextSession();

      public ConstantPerSec(Phase.ConstantPerSec def) {
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
                  return;
               }
               ++startedUsers;
               // TODO: after many iterations there will be some skew due to imprecise double calculations
               // Maybe we could restart from the expected rate every 1000th session?
               nextScheduled += nextSession();
            }
            nextDelta = (long) Math.ceil(nextScheduled);
         } else {
            int required = (int) (delta * def.usersPerSec / 1000);
            // mathematically, the formula below should be 1000 * (startedUsers + 1) / usersPerSec but while
            // integer division is rounding down, we're trying to round up
            nextDelta = (long) ((1000 * (startedUsers + 1) + def.usersPerSec - 1) / def.usersPerSec);
            for (int i = required - startedUsers; i > 0; --i) {
               if (startNewSession()) {
                  return;
               }
            }
            startedUsers = Math.max(startedUsers, required);
         }

         if (trace) {
            log.trace("{}: {} after start, {} started, next user in {} ms", def.name, delta, startedUsers, nextDelta - delta);
         }
         executorGroup.schedule(() -> proceed(executorGroup), nextDelta - delta, TimeUnit.MILLISECONDS);
      }

      private double nextSession() {
         return 1000 * -Math.log(Math.max(1e-20, random.nextDouble())) / def.usersPerSec;
      }

      @Override
      public void reserveSessions() {
         sessionPool.reserve(def.maxSessionsEstimate);
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
         int numActive = activeSessions.incrementAndGet();
         if (trace) {
            log.trace("{} has {} active sessions", def.name, numActive);
         }
         Session session = sessionPool.acquire();
         if (session != null) {
            session.start(this);
         } else {
            notifyFinished(null);
         }
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
