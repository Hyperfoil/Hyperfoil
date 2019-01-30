package io.hyperfoil.core.impl;

import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.core.session.SharedDataImpl;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Simulation;
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunnerImpl implements SimulationRunner {
    protected static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    protected final Simulation simulation;
    protected final Map<String, PhaseInstance> instances = new HashMap<>();
    protected final List<Session> sessions = new ArrayList<>();
    protected final Map<String, SharedResources> sharedResources = new HashMap<>();
    protected final EventLoopGroup eventLoopGroup;
    protected final Map<String, HttpClientPool> httpClientPools = new HashMap<>();
    protected final Map<EventExecutor, Map<String, HttpConnectionPool>> httpConnectionPools = new HashMap<>();

    public SimulationRunnerImpl(Simulation simulation) {
        this.eventLoopGroup = new NioEventLoopGroup(simulation.threads());
        this.simulation = simulation;
        for (Map.Entry<String, Http> http : simulation.http().entrySet()) {
            try {
                HttpClientPool httpClientPool = new HttpClientPoolImpl(eventLoopGroup, http.getValue());
                httpClientPools.put(http.getKey(), httpClientPool);
                if (http.getValue().isDefault()) {
                    httpClientPools.put(null, httpClientPool);
                }
                for (EventExecutor executor : eventLoopGroup) {
                    HttpConnectionPool httpConnectionPool = httpClientPool.connectionPool(executor);
                    Map<String, HttpConnectionPool> pools = httpConnectionPools.computeIfAbsent(executor, e -> new HashMap<>());
                    pools.put(http.getKey(), httpConnectionPool);
                    if (http.getValue().isDefault()) {
                        pools.put(null, httpConnectionPool);
                    }
                }
            } catch (SSLException e) {
                throw new IllegalStateException("Failed creating connection pool to " + http.getValue().baseUrl(), e);
            }
        }
    }

    @Override
    public void init(PhaseChangeHandler phaseChangeHandler, Handler<AsyncResult<Void>> handler) {
        //Initialise HttpClientPool
        ArrayList<Future> futures = new ArrayList<>();
        for (Map.Entry<String, HttpClientPool> entry : httpClientPools.entrySet()) {
            // default client pool is initialized by name
            if (entry.getKey() != null) {
                Future f = Future.future();
                futures.add(f);
                entry.getValue().start(f);
            }
        }

        for (Phase def : simulation.phases()) {
            SharedResources sharedResources;
            if (def.sharedResources == null) {
                // Noop phases don't use any resources
                sharedResources = SharedResources.NONE;
            } else if ((sharedResources = this.sharedResources.get(def.sharedResources)) == null) {
                sharedResources = new SharedResources(eventLoopGroup, def.scenario.sequences().length);
                List<Session> phaseSessions = sharedResources.sessions = new ArrayList<>();
                Map<EventExecutor, Statistics[]> statistics = sharedResources.statistics;
                Map<EventExecutor, SharedData> data = sharedResources.data;
                Supplier<Session> sessionSupplier = () -> {
                    Session session;
                    synchronized (this.sessions) {
                        session = SessionFactory.create(def.scenario, this.sessions.size());
                        this.sessions.add(session);
                    }
                    // We probably don't need to synchronize
                    synchronized (phaseSessions) {
                        phaseSessions.add(session);
                    }
                    EventLoop eventLoop = eventLoopGroup.next();
                    session.attach(eventLoop, data.get(eventLoop), httpConnectionPools.get(eventLoop), statistics.get(eventLoop));
                    return session;
                };
                sharedResources.sessionPool = new ConcurrentPoolImpl<>(sessionSupplier, () -> {
                    log.warn("Pool depleted, allocating new sessions!");
                    return sessionSupplier.get();
                });
                this.sharedResources.put(def.sharedResources, sharedResources);
            }
            PhaseInstance phase = PhaseInstanceImpl.newInstance(def);
            instances.put(def.name(), phase);
            Statistics[] allStats = sharedResources.statistics.values().stream().flatMap(Stream::of).toArray(Statistics[]::new);
            phase.setComponents(sharedResources.sessionPool, sharedResources.sessions, allStats, phaseChangeHandler);
            phase.reserveSessions();
        }

        CompositeFuture composite = CompositeFuture.join(futures);
        composite.setHandler(result -> handler.handle(result.mapEmpty()));
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

    public void visitStatistics(BiConsumer<Phase, Statistics[]> consumer) {
        for (SharedResources sharedResources : this.sharedResources.values()) {
            if (sharedResources.currentPhase == null) {
                // Phase(s) with these resources have not been started yet
                continue;
            }
            Phase phase = sharedResources.currentPhase.definition();
            for (Statistics[] statistics : sharedResources.statistics.values()) {
                consumer.accept(phase, statistics);
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
        }
        phaseInstance.start(eventLoopGroup);
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
        for (Map<String, HttpConnectionPool> pools : httpConnectionPools.values()) {
            for (Map.Entry<String, HttpConnectionPool> entry : pools.entrySet()) {
                if (entry.getKey() == null) {
                    // Ignore default pool: it's there twice
                    continue;
                }
                HttpConnectionPool pool = entry.getValue();
                Collection<? extends HttpConnection> connections = pool.connections();
                int available = 0;
                int inFlight = 0;
                for (HttpConnection conn : connections) {
                    if (conn.isAvailable()) {
                        available++;
                    }
                    inFlight += conn.inFlight();
                }
                list.add(String.format("%s: %d/%d available, %d in-flight requests, %d waiting sessions (estimate)", entry.getKey(), available, connections.size(), inFlight, pool.waitingSessions()));
            }
        }
        return list;
    }

    private static class SharedResources {
        static final SharedResources NONE = new SharedResources(null, 0);

        PhaseInstance currentPhase;
        ConcurrentPoolImpl<Session> sessionPool;
        List<Session> sessions;
        Map<EventExecutor, Statistics[]> statistics = new HashMap<>();
        Map<EventExecutor, SharedData> data = new HashMap<>();

        SharedResources(EventExecutorGroup executors, int sequences) {
            if (executors != null) {
                for (EventExecutor executor : executors) {
                    Statistics[] statistics = new Statistics[sequences];
                    for (int i = 0; i < sequences; i++) {
                        statistics[i] = new Statistics();
                    }
                    this.statistics.put(executor, statistics);
                    this.data.put(executor, new SharedDataImpl());
                }
            }
        }
    }
}