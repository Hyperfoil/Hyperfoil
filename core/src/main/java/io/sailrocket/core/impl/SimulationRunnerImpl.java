package io.sailrocket.core.impl;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
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

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunnerImpl implements SimulationRunner {
    protected final Simulation simulation;
    protected final Map<String, PhaseInstance> instances = new HashMap<>();

    protected HttpClientPool clientPool;
    protected List<Session> sessions = new ArrayList<>();
    protected Map<String, SharedResources> sharedResources = new HashMap<>();
    protected EventLoopGroup eventLoopGroup;

    public SimulationRunnerImpl(Simulation simulation) {
        this.eventLoopGroup = new NioEventLoopGroup(simulation.threads());
        this.simulation = simulation;
        HttpClientPool httpClientPool = null;
        try {
            httpClientPool = new HttpClientPoolImpl(eventLoopGroup, simulation.http());
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.clientPool = httpClientPool;
    }

    @Override
    public void init(BiConsumer<String, PhaseInstance.Status> phaseChangeHandler) {
        //Initialise HttpClientPool
        CountDownLatch latch = new CountDownLatch(1);
        clientPool.start(latch::countDown);

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
                    HttpConnectionPool httpConnectionPool = clientPool.next();
                    session.attach(httpConnectionPool);
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
        clientPool.shutdown();
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
        instances.get(phase).start(clientPool.executors());
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