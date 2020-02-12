package io.hyperfoil.core.impl;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.connection.HttpDestinationTable;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.core.client.netty.HttpDestinationTableImpl;
import io.hyperfoil.core.client.netty.PrivateConnectionPool;
import io.hyperfoil.core.session.SharedDataImpl;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.connection.HttpClientPool;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.session.PhaseChangeHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.api.SimulationRunner;
import io.hyperfoil.core.client.netty.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.net.ssl.SSLException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunnerImpl implements SimulationRunner {
   protected static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

   protected final Benchmark benchmark;
   protected final int agentId;
   protected final Map<String, PhaseInstance> instances = new HashMap<>();
   protected final List<Session> sessions = new ArrayList<>();
   private final Map<String, SharedResources> sharedResources = new HashMap<>();
   protected final NioEventLoopGroup eventLoopGroup;
   protected final EventExecutor[] executors;
   protected final Map<String, HttpClientPool> httpClientPools = new HashMap<>();
   protected final HttpDestinationTableImpl[] httpDestinations;
   private final Queue<Phase> toPrune;
   private PhaseChangeHandler phaseChangeHandler;
   private boolean isDepletedMessageQuietened;

   public SimulationRunnerImpl(Benchmark benchmark, int agentId) {
      this.eventLoopGroup = new NioEventLoopGroup(benchmark.threads());
      this.executors = StreamSupport.stream(eventLoopGroup.spliterator(), false).toArray(EventExecutor[]::new);
      this.benchmark = benchmark;
      this.agentId = agentId;
      this.httpDestinations = new HttpDestinationTableImpl[executors.length];
      this.toPrune = new ArrayBlockingQueue<>(benchmark.phases().size());
      @SuppressWarnings("unchecked")
      Map<String, HttpConnectionPool>[] httpConnectionPools = new Map[executors.length];
      for (Map.Entry<String, Http> http : benchmark.http().entrySet()) {
         try {
            HttpClientPool httpClientPool = new HttpClientPoolImpl(eventLoopGroup, http.getValue());
            httpClientPools.put(http.getKey(), httpClientPool);
            if (http.getValue().isDefault()) {
               httpClientPools.put(null, httpClientPool);
            }

            for (int executorId = 0; executorId < executors.length; ++executorId) {
               HttpConnectionPool httpConnectionPool = httpClientPool.connectionPool(executors[executorId]);
               Map<String, HttpConnectionPool> pools = httpConnectionPools[executorId];
               if (pools == null) {
                  httpConnectionPools[executorId] = pools = new HashMap<>();
               }
               pools.put(http.getKey(), httpConnectionPool);
               if (http.getValue().isDefault()) {
                  pools.put(null, httpConnectionPool);
               }
            }
         } catch (SSLException e) {
            throw new IllegalStateException("Failed creating connection pool to " + http.getValue().host() + ":" + http.getValue().port(), e);
         }
      }
      for (int executorId = 0; executorId < httpConnectionPools.length; executorId++) {
         Map<String, HttpConnectionPool> pools = httpConnectionPools[executorId];
         httpDestinations[executorId] = new HttpDestinationTableImpl(pools);
      }
   }

   public void setPhaseChangeHandler(PhaseChangeHandler phaseChangeHandler) {
      this.phaseChangeHandler = phaseChangeHandler;
   }

   @Override
   public void init(Handler<AsyncResult<Void>> handler) {
      //Initialise HttpClientPool
      ArrayList<Future> futures = new ArrayList<>();
      for (Map.Entry<String, HttpClientPool> entry : httpClientPools.entrySet()) {
         // default client pool is initialized by name
         if (entry.getKey() != null) {
            Promise<Void> promise = Promise.promise();
            futures.add(promise.future());
            entry.getValue().start(promise);
         }
      }

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
                  int sessionId = this.sessions.size();
                  executorId = sessionId % executors.length;
                  session = SessionFactory.create(def.scenario, agentId, executorId, sessionId);
                  this.sessions.add(session);
               }
               // We probably don't need to synchronize
               synchronized (phaseSessions) {
                  phaseSessions.add(session);
               }
               HttpDestinationTable httpDestinations = this.httpDestinations[executorId];
               if (benchmark.ergonomics().privateHttpPools()) {
                  httpDestinations = new HttpDestinationTableImpl(httpDestinations, PrivateConnectionPool::new);
               }
               session.attach(executors[executorId], data[executorId], httpDestinations, statistics[executorId]);
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
         PhaseInstance phase = PhaseInstanceImpl.newInstance(def);
         instances.put(def.name(), phase);
         phase.setComponents(sharedResources.sessionPool, sharedResources.sessions, this::phaseChanged);
         phase.reserveSessions();
         // at this point all session resources should be reserved
      }

      CompositeFuture composite = CompositeFuture.join(futures);
      composite.setHandler(result -> {
         if (result.failed()) {
            log.error("One of the HTTP client pools failed to start.");
         }
         handler.handle(result.mapEmpty());
      });
   }

   protected void phaseChanged(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error) {
      if (status == PhaseInstance.Status.TERMINATED) {
         SharedResources resources = this.sharedResources.get(phase.sharedResources);
         if (resources != null && resources.statistics != null) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < executors.length; ++i) {
               SessionStatistics statistics = resources.statistics[i];
               if (executors[i].inEventLoop()) {
                  applyToPhase(statistics, phase, now, Statistics::end);
               } else {
                  try {
                     executors[i].submit(() -> applyToPhase(statistics, phase, now, Statistics::end)).get();
                  } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                     log.error("Interrupted waiting for statistics.end()");
                  } catch (ExecutionException e) {
                     log.error("Failed to end statistics!", e);
                     if (error == null) {
                        error = e;
                     }
                  }
               }
            }
         }
      }
      if (phaseChangeHandler != null) {
         phaseChangeHandler.onChange(phase, status, sessionLimitExceeded, error);
      }
      if (status == PhaseInstance.Status.TERMINATED) {
         toPrune.add(phase);
      }
   }

   @Override
   public void shutdown() {
      for (HttpClientPool pool : httpClientPools.values()) {
         pool.shutdown();
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

   // This method should be invoked only from vert.x eventpool thread
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
         int minUsed = sharedResources.sessionPool.minUsed();
         int maxUsed = sharedResources.sessionPool.maxUsed();
         sharedResources.sessionPool.resetStats();
         if (minUsed <= maxUsed && maxUsed != 0) {
            consumer.accept(sharedResources.currentPhase.definition().name(), minUsed, maxUsed);
         }
      }
   }

   public void visitSessionPoolStats(Phase phase, SessionStatsConsumer consumer) {
      SharedResources sharedResources = this.sharedResources.get(phase.sharedResources);
      if (sharedResources != null) {
         int minUsed = sharedResources.sessionPool.minUsed();
         int maxUsed = sharedResources.sessionPool.maxUsed();
         sharedResources.sessionPool.resetStats();
         if (minUsed < maxUsed) {
            consumer.accept(phase.name(), minUsed, maxUsed);
         }
      }
   }

   @Override
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

   @Override
   public void finishPhase(String phase) {
      instances.get(phase).finish();
   }

   @Override
   public void tryTerminatePhase(String phase) {
      instances.get(phase).tryTerminate();
   }

   @Override
   public void terminatePhase(String phase) {
      instances.get(phase).terminate();
   }

   public List<String> listConnections() {
      ArrayList<String> list = new ArrayList<>();
      // Connection pools should be accessed only from the executor, but since we're only publishing stats...
      for (HttpDestinationTableImpl destinations : httpDestinations) {
         for (Map.Entry<String, HttpConnectionPool> entry : destinations.iterable()) {
            if (entry.getKey() == null) {
               // Ignore default pool: it's there twice
               continue;
            }
            HttpConnectionPool pool = entry.getValue();
            Collection<? extends HttpConnection> connections = pool.connections();
            Map<String, AtomicInteger> byType = new HashMap<>();
            int available = 0;
            int inFlight = 0;
            for (HttpConnection conn : connections) {
               if (conn.isAvailable()) {
                  available++;
               }
               inFlight += conn.inFlight();
               byType.computeIfAbsent(conn.getClass().getSimpleName() + (conn.isSecure() ? "(SSL)" : ""), k -> new AtomicInteger()).incrementAndGet();
            }
            list.add(String.format("%s: %d/%d available, %d in-flight requests, %d waiting sessions (estimate), types: %s", entry.getKey(), available, connections.size(), inFlight, pool.waitingSessions(), byType));
         }
      }
      return list;
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