package io.hyperfoil.core.impl;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.AgentData;
import io.hyperfoil.api.session.ControllerListener;
import io.hyperfoil.api.session.GlobalData;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.session.AgentDataImpl;
import io.hyperfoil.core.session.GlobalDataImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.session.ThreadDataImpl;
import io.hyperfoil.core.util.CpuWatchdog;
import io.hyperfoil.impl.Util;
import io.hyperfoil.internal.Properties;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunner {
   protected static final Logger log = LogManager.getLogger(SimulationRunner.class);

   private static final Clock DEFAULT_CLOCK = Clock.systemDefaultZone();

   protected final Benchmark benchmark;
   protected final int agentId;
   protected final String runId;
   protected final Map<String, PhaseInstance> instances = new HashMap<>();
   protected final List<Session> sessions = new ArrayList<>();
   private final Map<String, SharedResources> sharedResources = new HashMap<>();
   protected final EventLoopGroup eventLoopGroup;
   protected final EventLoop[] executors;
   private final Queue<Phase> toPrune;
   private final PluginRunData[] runData;
   private ControllerListener controllerListener;
   private final Consumer<Throwable> errorHandler;
   private boolean isDepletedMessageQuietened;
   private Thread jitterWatchdog;
   private CpuWatchdog cpuWatchdog;
   private final GlobalDataImpl[] globalData;
   private final GlobalDataImpl.Collector globalCollector = new GlobalDataImpl.Collector();

   public SimulationRunner(Benchmark benchmark, String runId, int agentId, Consumer<Throwable> errorHandler) {
      this.eventLoopGroup = EventLoopFactory.INSTANCE.create(benchmark.threads(agentId));
      this.executors = StreamSupport.stream(eventLoopGroup.spliterator(), false).map(EventLoop.class::cast).toArray(EventLoop[]::new);
      this.benchmark = benchmark;
      this.runId = runId;
      this.agentId = agentId;
      if (benchmark.phases().isEmpty()) {
         throw new BenchmarkDefinitionException("This benchmark does not have any phases, nothing to do!");
      }
      this.toPrune = new ArrayBlockingQueue<>(benchmark.phases().size());
      this.runData = benchmark.plugins().stream()
            .map(config -> Plugin.lookup(config).createRunData(benchmark, executors, agentId))
            .toArray(PluginRunData[]::new);
      this.errorHandler = errorHandler;
      this.globalData = Arrays.stream(executors).map(GlobalDataImpl::new).toArray(GlobalDataImpl[]::new);
   }

   public void setControllerListener(ControllerListener controllerListener) {
      this.controllerListener = controllerListener;
   }

   public void init() {
      long initSimulationStartTime = System.currentTimeMillis();
      AgentData agentData = new AgentDataImpl();
      ThreadData[] threadData = new ThreadData[executors.length];
      Arrays.setAll(threadData, executorId -> new ThreadDataImpl());
      for (Phase def : benchmark.phases()) {
         SharedResources sharedResources;
         if (def.sharedResources == null) {
            // Noop phases don't use any resources
            sharedResources = SharedResources.NONE;
         } else if ((sharedResources = this.sharedResources.get(def.sharedResources)) == null) {
            sharedResources = new SharedResources(executors.length);
            List<Session> phaseSessions = sharedResources.sessions = new ArrayList<>();
            SessionStatistics[] statistics = sharedResources.statistics;
            Supplier<Session> sessionSupplier = () -> {
               Session session;
               int executorId;
               synchronized (this.sessions) {
                  // We need to set executor based on the id within phase (shared resources) because
                  // if the connection pool size = number of users we need to match the #sessions in
                  // each executor to the #connections.
                  executorId = phaseSessions.size() % executors.length;
                  session = SessionFactory.create(def.scenario, executorId, this.sessions.size());
                  this.sessions.add(session);
                  phaseSessions.add(session);
               }
               session.attach(executors[executorId], threadData[executorId], agentData, globalData[executorId], statistics[executorId]);
               for (int i = 0; i < runData.length; ++i) {
                  runData[i].initSession(session, executorId, def.scenario, DEFAULT_CLOCK);
               }
               session.reserve(def.scenario);
               return session;
            };
            SharedResources finalSharedResources = sharedResources;
            sharedResources.sessionPool = new ElasticPoolImpl<>(sessionSupplier, () -> {
               if (!isDepletedMessageQuietened) {
                  log.warn("Pool depleted, throttling execution! Enable trace logging to see subsequent pool depletion messages.");
                  isDepletedMessageQuietened = true;
               } else {
                  log.trace("Pool depleted, throttling execution!");
               }
               finalSharedResources.currentPhase.setSessionLimitExceeded();
               return null;
            });
            this.sharedResources.put(def.sharedResources, sharedResources);
         }
         PhaseInstance phase = PhaseInstanceImpl.newInstance(def, runId, agentId);
         instances.put(def.name(), phase);
         phase.setComponents(sharedResources.sessionPool, sharedResources.sessions, this::phaseChanged);
         phase.reserveSessions();
         // at this point all session resources should be reserved
      }
      // hint the GC to tenure sessions
      this.runGC();

      jitterWatchdog = new Thread(this::observeJitter, "jitter-watchdog");
      jitterWatchdog.setDaemon(true);

      cpuWatchdog = new CpuWatchdog(errorHandler, () -> instances.values().stream().anyMatch(p -> !p.definition().isWarmup));
      cpuWatchdog.start();

      log.info("Simulation initialization took {} ms", System.currentTimeMillis() - initSimulationStartTime);
   }

   public void openConnections(Function<Callable<Void>, Future<Void>> blockingHandler, Handler<AsyncResult<Void>> handler) {
      @SuppressWarnings("rawtypes") ArrayList<Future> futures = new ArrayList<>();
      for (PluginRunData plugin : runData) {
         plugin.openConnections(blockingHandler, futures::add);
      }

      CompositeFuture composite = CompositeFuture.join(futures);
      composite.onComplete(result -> {
         if (result.failed()) {
            log.error("One of the HTTP client pools failed to start.");
         }
         handler.handle(result.mapEmpty());
         jitterWatchdog.start();
      });
   }

   private void observeJitter() {
      long period = Properties.getLong(Properties.JITTER_WATCHDOG_PERIOD, 50);
      long threshold = Properties.getLong(Properties.JITTER_WATCHDOG_THRESHOLD, 100);
      long lastTimestamp = System.nanoTime();
      while (true) {
         try {
            Thread.sleep(period);
         } catch (InterruptedException e) {
            log.debug("Interrupted, terminating jitter watchdog");
            return;
         }
         long currentTimestamp = System.nanoTime();
         long delay = TimeUnit.NANOSECONDS.toMillis(currentTimestamp - lastTimestamp);
         if (delay > threshold) {
            String message = String.format("%s | Jitter watchdog was not invoked for %d ms (threshold is %d ms); please check your GC settings.",
                  new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()), delay, threshold);
            log.error(message);
            errorHandler.accept(new BenchmarkExecutionException(message));
         }
         lastTimestamp = currentTimestamp;
      }
   }

   protected CompletableFuture<Void> phaseChanged(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error) {
      if (!phase.isWarmup) {
         if (status == PhaseInstance.Status.RUNNING) {
            cpuWatchdog.notifyPhaseStart(phase.name);
         } else if (status.isFinished()) {
            cpuWatchdog.notifyPhaseEnd(phase.name);
         }
      }
      if (status == PhaseInstance.Status.TERMINATED) {
         return completePhase(phase).whenComplete((nil, e) -> notifyAndScheduleForPruning(phase, status, sessionLimitExceeded, error != null ? error : e, globalCollector.extract()));
      } else {
         notifyAndScheduleForPruning(phase, status, sessionLimitExceeded, error, null);
         return Util.COMPLETED_VOID_FUTURE;
      }
   }

   private CompletableFuture<Void> completePhase(Phase phase) {
      SharedResources resources = this.sharedResources.get(phase.sharedResources);
      if (resources == null) {
         return Util.COMPLETED_VOID_FUTURE;
      }
      List<CompletableFuture<Void>> futures = new ArrayList<>(executors.length);
      long now = System.currentTimeMillis();
      for (int i = 0; i < executors.length; ++i) {
         SessionStatistics statistics = resources.statistics[i];
         if (executors[i].inEventLoop()) {
            if (statistics != null) {
               applyToPhase(statistics, phase, now, Statistics::end);
            }
            globalCollector.collect(phase.name, globalData[i]);
         } else {
            CompletableFuture<Void> cf = new CompletableFuture<>();
            futures.add(cf);
            GlobalDataImpl gd = globalData[i];
            executors[i].execute(() -> {
               try {
                  globalCollector.collect(phase.name, gd);
                  if (statistics != null) {
                     applyToPhase(statistics, phase, now, Statistics::end);
                  }
                  cf.complete(null);
               } catch (Throwable t) {
                  cf.completeExceptionally(t);
               }
            });
         }
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
   }

   private void notifyAndScheduleForPruning(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error, Map<String, GlobalData.Element> globalData) {
      if (controllerListener != null) {
         controllerListener.onPhaseChange(phase, status, sessionLimitExceeded, error, globalData);
      }
      if (status == PhaseInstance.Status.TERMINATED) {
         toPrune.add(phase);
      }
   }

   public void shutdown() {
      if (jitterWatchdog != null) {
         jitterWatchdog.interrupt();
      }
      if (cpuWatchdog != null) {
         cpuWatchdog.stop();
      }
      for (PluginRunData plugin : runData) {
         plugin.shutdown();
      }
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
      for (Session session : sessions) {
         SessionFactory.destroy(session);
      }
   }

   public void visitSessions(Consumer<Session> consumer) {
      synchronized (sessions) {
         for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            consumer.accept(session);
         }
      }
   }

   // This method should be invoked only from vert.x event-loop thread
   public void visitStatistics(Consumer<SessionStatistics> consumer) {
      for (SharedResources sharedResources : this.sharedResources.values()) {
         if (sharedResources.currentPhase == null) {
            // Phase(s) with these resources have not been started yet
            continue;
         }
         for (SessionStatistics statistics : sharedResources.statistics) {
            consumer.accept(statistics);
         }
      }
      Phase phase;
      while ((phase = toPrune.poll()) != null) {
         Phase phase2 = phase;
         for (SharedResources sharedResources : this.sharedResources.values()) {
            if (sharedResources.statistics == null) {
               continue;
            }
            SessionStatistics[] sessionStatistics = sharedResources.statistics;
            for (int i = 0; i < sessionStatistics.length; i++) {
               SessionStatistics statistics = sessionStatistics[i];
               executors[i].execute(() -> statistics.prune(phase2));
            }
         }
      }
   }

   public void visitStatistics(Phase phase, Consumer<SessionStatistics> consumer) {
      SharedResources sharedResources = this.sharedResources.get(phase.sharedResources);
      if (sharedResources == null || sharedResources.statistics == null) {
         return;
      }
      for (SessionStatistics statistics : sharedResources.statistics) {
         consumer.accept(statistics);
      }
   }

   public void visitSessionPoolStats(SessionStatsConsumer consumer) {
      for (SharedResources sharedResources : this.sharedResources.values()) {
         if (sharedResources.currentPhase == null) {
            // Phase(s) with these resources have not been started yet
            continue;
         }
         recordSessionStats(sharedResources.sessionPool, sharedResources.currentPhase.definition().name(), consumer);
      }
   }

   public void visitSessionPoolStats(Phase phase, SessionStatsConsumer consumer) {
      SharedResources sharedResources = this.sharedResources.get(phase.sharedResources);
      if (sharedResources != null) {
         recordSessionStats(sharedResources.sessionPool, phase.name(), consumer);
      }
   }

   private void recordSessionStats(ElasticPoolImpl<Session> sessionPool, String phaseName, SessionStatsConsumer consumer) {
      int minUsed = sessionPool.minUsed();
      int maxUsed = sessionPool.maxUsed();
      sessionPool.resetStats();
      if (minUsed <= maxUsed && maxUsed != 0) {
         consumer.accept(phaseName, minUsed, maxUsed);
      }
   }

   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      for (PluginRunData plugin : runData) {
         plugin.visitConnectionStats(consumer);
      }
   }

   public void startPhase(String phase) {
      PhaseInstance phaseInstance = instances.get(phase);
      SharedResources sharedResources = this.sharedResources.get(phaseInstance.definition().sharedResources);
      if (sharedResources != null) {
         // Avoid NPE in noop phases
         sharedResources.currentPhase = phaseInstance;
         if (sharedResources.statistics != null) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < executors.length; ++i) {
               SessionStatistics statistics = sharedResources.statistics[i];
               executors[i].execute(() -> applyToPhase(statistics, phaseInstance.definition(), now, Statistics::start));
            }
         }
      }
      phaseInstance.start(eventLoopGroup);
   }

   private void applyToPhase(SessionStatistics statistics, Phase phase, long now, BiConsumer<Statistics, Long> f) {
      for (int j = 0; j < statistics.size(); ++j) {
         if (statistics.phase(j) == phase) {
            for (Statistics s : statistics.stats(j).values()) {
               f.accept(s, now);
            }
         }
      }
   }

   public void finishPhase(String phase) {
      instances.get(phase).finish();
   }

   public void tryTerminatePhase(String phase) {
      instances.get(phase).tryTerminate();
   }

   public void terminatePhase(String phase) {
      instances.get(phase).terminate();
   }

   public List<String> listConnections() {
      ArrayList<String> list = new ArrayList<>();
      for (PluginRunData plugin : runData) {
         plugin.listConnections(list::add);
      }
      return list;
   }

   public String getCpuUsage(String name) {
      return cpuWatchdog.getCpuUsage(name);
   }

   public void addGlobalData(Map<String, GlobalData.Element> globalData) {
      for (int i = 0; i < executors.length; ++i) {
         GlobalDataImpl data = this.globalData[i];
         executors[i].execute(() -> {
            data.add(globalData);
         });
      }
   }

   /**
    * It forces the garbage collection process.
    * If the JVM arg {@link Properties#GC_CHECK} is set, it will also perform an additional check to ensure that the
    * GC actually occurred.
    *
    * <p>Note: This method uses {@link ManagementFactory#getGarbageCollectorMXBeans()} to gather GC bean information
    * and checks if at least one GC cycle has completed. It waits for the GC to stabilize within a maximum wait time.
    * If GC beans are not available, it waits pessimistically for a defined period after invoking {@code System.gc()}.
    * </p>
    *
    * @see #runSystemGC()
    * @see ManagementFactory#getGarbageCollectorMXBeans()
    */
   private void runGC() {
      long start = System.currentTimeMillis();

      if (!Properties.getBoolean(Properties.GC_CHECK)) {
         this.runSystemGC();
      } else {
         // Heavily inspired by
         // https://github.com/openjdk/jmh/blob/6d6ce6315dc39d1d3abd0e3ac9eca9c38f767112/jmh-core/src/main/java/org/openjdk/jmh/runner/BaseRunner.java#L309
         log.info("Running additional GC check!");
         // Collect GC beans
         List<GarbageCollectorMXBean> enabledBeans = new ArrayList<>();

         long beforeGcCount = 0;
         for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            if (count != -1) {
               enabledBeans.add(bean);
               beforeGcCount += bean.getCollectionCount();
            }
         }

         // Run the GC
         this.runSystemGC();

         // Make sure the GC actually happened
         //   a) That at least two collections happened, indicating GC work.
         //   b) That counter updates have not happened for a while, indicating GC work had ceased.

         final int MAX_WAIT_MS = 10 * 1000;

         if (enabledBeans.isEmpty()) {
            log.warn("MXBeans can not report GC info. System.gc() invoked, but cannot check whether GC actually happened!");
            return;
         }

         boolean gcHappened = false;
         boolean stable = false;

         long checkStart = System.nanoTime();
         while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - checkStart) < MAX_WAIT_MS) {
            long afterGcCount = 0;
            for (GarbageCollectorMXBean bean : enabledBeans) {
               afterGcCount += bean.getCollectionCount();
            }

            if (!gcHappened) {
               if (afterGcCount - beforeGcCount >= 2) {
                  gcHappened = true;
               }
            } else {
               if (afterGcCount == beforeGcCount) {
                  // Stable!
                  stable = true;
                  break;
               }
               beforeGcCount = afterGcCount;
            }

            try {
               TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }

         if (!stable) {
            if (gcHappened) {
               log.warn("System.gc() was invoked but unable to wait while GC stopped, is GC too asynchronous?");
            } else {
               log.warn("System.gc() was invoked but couldn't detect a GC occurring, is System.gc() disabled?");
            }
         }
      }

      log.info("Overall GC run took {} ms", (System.currentTimeMillis() - start));
   }

   /**
    * Executes the garbage collector (GC) and forces object finalization before each GC run.
    * The method runs the garbage collector twice to ensure maximum garbage collection,
    * and forces finalization of objects before each GC execution. It logs the time taken
    * to complete the garbage collection process.
    *
    * @see System#runFinalization()
    * @see System#gc()
    */
   private void runSystemGC() {
      long actualGCRun = System.currentTimeMillis();

      // Run the GC twice and force finalization before every GC run
      System.runFinalization();
      System.gc();
      System.runFinalization();
      System.gc();

      log.info("GC execution took {} ms", (System.currentTimeMillis() - actualGCRun));
   }

   private static class SharedResources {
      static final SharedResources NONE = new SharedResources(0);

      PhaseInstance currentPhase;
      ElasticPoolImpl<Session> sessionPool;
      List<Session> sessions;
      SessionStatistics[] statistics;

      SharedResources(int executorCount) {
         statistics = new SessionStatistics[executorCount];
         for (int executorId = 0; executorId < executorCount; ++executorId) {
            this.statistics[executorId] = new SessionStatistics();
         }
      }
   }
}