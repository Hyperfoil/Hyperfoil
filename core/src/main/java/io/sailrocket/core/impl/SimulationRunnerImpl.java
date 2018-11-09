package io.sailrocket.core.impl;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.sailrocket.api.config.Http;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.api.SimulationRunner;
import io.sailrocket.core.client.netty.HttpClientPoolImpl;
import io.sailrocket.core.session.SessionFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunnerImpl implements SimulationRunner {
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
    public void init(BiConsumer<String, PhaseInstance.Status> phaseChangeHandler) {
        //Initialise HttpClientPool
        CountDownLatch latch = new CountDownLatch(httpClientPools.size());
        for (Map.Entry<String, HttpClientPool> entry : httpClientPools.entrySet()) {
            if (entry.getKey() == null) {
                // default client pool is initialized by name
                latch.countDown();
            } else {
                entry.getValue().start(latch::countDown);
            }
        }

        for (Phase def : simulation.phases()) {
            PhaseInstance phase = PhaseInstanceImpl.newInstance(def);
            instances.put(def.name(), phase);
            SharedResources sharedResources;
            if (def.sharedResources == null) {
                // Noop phases don't use any resources
                sharedResources = SharedResources.NONE;
            } else if ((sharedResources = this.sharedResources.get(def.sharedResources)) == null) {
                sharedResources = new SharedResources();
                List<Session> phaseSessions = sharedResources.sessions = new ArrayList<>();
                sharedResources.sessionPool = new ConcurrentPoolImpl<>(() -> {
                    Session session;
                    synchronized (this.sessions) {
                        session = SessionFactory.create(phase.definition().scenario, this.sessions.size());
                        this.sessions.add(session);
                    }
                    // We probably don't need to synchronize
                    synchronized (phaseSessions) {
                        phaseSessions.add(session);
                    }
                    EventLoop eventLoop = eventLoopGroup.next();
                    session.attach(eventLoop, httpConnectionPools.get(eventLoop));
                    return session;
                });
                this.sharedResources.put(def.sharedResources, sharedResources);
            }
            phase.setComponents(sharedResources.sessionPool, sharedResources.sessions, phaseChangeHandler);
            phase.reserveSessions();
        }

        try {
            latch.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    @Override
    public void startPhase(String phase) {
        instances.get(phase).start(eventLoopGroup);
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

    private static class SharedResources {
        static final SharedResources NONE = new SharedResources();

        ConcurrentPoolImpl<Session> sessionPool;
        List<Session> sessions;
    }
}