package io.hyperfoil.core.impl;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.core.api.PluginRunData;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.session.SharedDataImpl;
import io.hyperfoil.core.util.CpuWatchdog;
import io.hyperfoil.impl.Util;
import io.hyperfoil.internal.Properties;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
   private PhaseChangeHandler phaseChangeHandler;
   private final Consumer<Throwable> errorHandler;
   private boolean isDepletedMessageQuietened;
   private Thread jitterWatchdog;
   private CpuWatchdog cpuWatchdog;

   public SimulationRunner(Benchmark benchmark, String runId, int agentId, Consumer<Throwable> errorHandler) {
      this.eventLoopGroup = EventLoopFactory.INSTANCE.create(benchmark.threads(agentId));
      this.executors = StreamSupport.stream(eventLoopGroup.spliterator(), false).map(EventLoop.class::cast).toArray(EventLoop[]::new);
      this.benchmark = benchmark;
      this.runId = runId;
      this.agentId = agentId;
      this.toPrune = new ArrayBlockingQueue<>(benchmark.phases().size());
      this.runData = benchmark.plugins().stream()
            .map(config -> Plugin.lookup(config).createRunData(benchmark, executors, agentId))
            .toArray(PluginRunData[]::new);
      this.errorHandler = errorHandler;
   }

   public void setPhaseChangeHandler(PhaseChangeHandler phaseChangeHandler) {
      this.phaseChangeHandler = phaseChangeHandler;
   }

   public void init() {
      for (Phase def : benchmark.phases()) {
         SharedResources sharedResources;
         if (def.sharedResources == null) {
            // Noop phases don't use any resources
            sharedResources = SharedResources.NONE;
         } else if ((sharedResources = this.sharedResources.get(def.sharedResources)) == null) {
            sharedResources = new SharedResources(executors.length);
            List<Session> phaseSessions = sharedResources.sessions = new ArrayList<>();
            SessionStatistics[] statistics = sharedResources.statistics;
            SharedData[] data = sharedResources.data;
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
               session.attach(executors[executorId], data[executorId], statistics[executorId]);
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
      System.gc();

      jitterWatchdog = new Thread(this::observeJitter, "jitter-watchdog");
      jitterWatchdog.setDaemon(true);

      cpuWatchdog = new CpuWatchdog(errorHandler, () -> instances.values().stream().anyMatch(p -> !p.definition().isWarmup));
      cpuWatchdog.start();
   }

   public void openConnections(Handler<AsyncResult<Void>> handler) {
      @SuppressWarnings("rawtypes") ArrayList<Future> futures = new ArrayList<>();
      for (PluginRunData plugin : runData) {
         plugin.openConnections(futures::add);
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
         return terminateStatistics(phase).whenComplete(
               (nil, e) -> notifyAndScheduleForPruning(phase, status, sessionLimitExceeded, error != null ? error : e));
      } else {
         notifyAndScheduleForPruning(phase, status, sessionLimitExceeded, error);
         return Util.COMPLETED_VOID_FUTURE;
      }
   }

   private CompletableFuture<Void> terminateStatistics(Phase phase) {
      SharedResources resources = this.sharedResources.get(phase.sharedResources);
      if (resources == null || resources.statistics == null) {
         return Util.COMPLETED_VOID_FUTURE;
      }
      List<CompletableFuture<Void>> futures = new ArrayList<>(executors.length);
      long now = System.currentTimeMillis();
      for (int i = 0; i < executors.length; ++i) {
         SessionStatistics statistics = resources.statistics[i];
         if (executors[i].inEventLoop()) {
            applyToPhase(statistics, phase, now, Statistics::end);
         } else {
            CompletableFuture<Void> cf = new CompletableFuture<>();
            futures.add(cf);
            executors[i].execute(() -> {
               try {
                  applyToPhase(statistics, phase, now, Statistics::end);
                  cf.complete(null);
               } catch (Throwable t) {
                  cf.completeExceptionally(t);
               }
            });
         }
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
   }

   private void notifyAndScheduleForPruning(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error) {
      if (phaseChangeHandler != null) {
         phaseChangeHandler.onChange(phase, status, sessionLimitExceeded, error);
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

   private static class SharedResources {
      static final SharedResources NONE = new SharedResources(0);

      PhaseInstance currentPhase;
      ElasticPoolImpl<Session> sessionPool;
      List<Session> sessions;
      SessionStatistics[] statistics;
      SharedData[] data;

      SharedResources(int executorCount) {
         statistics = new SessionStatistics[executorCount];
         data = new SharedData[executorCount];
         for (int executorId = 0; executorId < executorCount; ++executorId) {
            this.statistics[executorId] = new SessionStatistics();
            this.data[executorId] = new SharedDataImpl();
         }
      }
   }
}