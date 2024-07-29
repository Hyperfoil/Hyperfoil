package io.hyperfoil.core.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.collection.ElasticPool;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.EventExecutorGroup;

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
   private Runnable failedSessionAcquisitionAction;

   public static PhaseInstance newInstance(Phase def, String runId, int agentId) {
      PhaseCtor ctor = constructors.get(def.model.getClass());
      if (ctor == null)
         throw new BenchmarkDefinitionException("Unknown phase type: " + def.model);
      return ctor.create(def, runId, agentId);
   }

   interface PhaseCtor {
      PhaseInstance create(Phase phase, String runId, int agentId);
   }

   static {
      constructors.put(Model.AtOnce.class, AtOnce::new);
      constructors.put(Model.Always.class, Always::new);
      constructors.put(Model.RampRate.class, OpenModel::rampRate);
      constructors.put(Model.ConstantRate.class, OpenModel::constantRate);
      constructors.put(Model.Sequentially.class, Sequentially::new);
      //noinspection StaticInitializerReferencesSubClass
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
      recordAbsoluteStartTime();
      log.debug("{} changing status to RUNNING", def.name);
      phaseChangeHandler.onChange(def, Status.RUNNING, false, error)
            .thenRun(() -> proceedOnStarted(executorGroup));
   }

   /**
    * This method is called when the phase is started and the status is set to RUNNING.<br>
    * It should be overridden by the subclasses to handle the very first call to {@link #proceed(EventExecutorGroup)}.
    */
   protected void proceedOnStarted(EventExecutorGroup executorGroup) {
      proceed(executorGroup);
   }

   /**
    * This method can be overridden (but still need to call super) to create additional timestamps
    * other than {@link #absoluteStartTime}.
    */
   protected void recordAbsoluteStartTime() {
      absoluteStartTime = System.currentTimeMillis();
      absoluteStartTimeString = String.valueOf(absoluteStartTime);
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
         //noinspection SynchronizeOnNonFinalField
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

   @Override
   public void runOnFailedSessionAcquisition(final Runnable action) {
      this.failedSessionAcquisitionAction = action;
   }

   // TODO better name
   @Override
   public void setComponents(ElasticPool<Session> sessionPool, List<Session> sessionList,
         PhaseChangeHandler phaseChangeHandler) {
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
         log.trace("#{} NotifyFinished, {} has {} active sessions", session == null ? -1 : session.uniqueId(), def.name,
               numActive);
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

   /**
    * @return {@code true} if the new {@link Session} was started, {@code false} otherwise.
    */
   protected boolean startNewSession() {
      int numActive = activeSessions.incrementAndGet();
      if (numActive < 0) {
         // finished
         return false;
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
         return false;
      }
      if (session == null) {
         noSessionsAvailable();
         return false;
      }
      session.start(this);
      return true;
   }

   private void noSessionsAvailable() {
      if (failedSessionAcquisitionAction != null) {
         failedSessionAcquisitionAction.run();
      }
      if (!sessionLimitExceeded) {
         sessionLimitExceeded = true;
      }
      notifyFinished(null);
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
                  log.warn("{} not terminating because it is already {}", def.name, status);
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
