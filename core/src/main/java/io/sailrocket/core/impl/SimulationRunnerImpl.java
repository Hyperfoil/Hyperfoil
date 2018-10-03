package io.sailrocket.core.impl;

import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.api.SimulationRunner;
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

    public SimulationRunnerImpl(Simulation simulation) {
        this.simulation = simulation;
        HttpClientPool httpClientPool = null;
        try {
            httpClientPool = simulation.httpClientPoolFactory().build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.clientPool = httpClientPool;
    }

    @Override
    public void init(BiConsumer<String, PhaseInstance.Status> phaseChangeHandler) {
        //Initialise HttpClientPool
        CountDownLatch latch = new CountDownLatch(1);
        clientPool.start(v1 -> {
            latch.countDown();
        });

        for (Phase def : simulation.phases()) {
            PhaseInstance phase = PhaseInstanceImpl.newInstance(def);
            instances.put(def.name(), phase);
            phase.setComponents(new ConcurrentPoolImpl<>(() -> {
                Session session;
                synchronized (sessions) {
                    session = SessionFactory.create(clientPool, phase, sessions.size());
                    sessions.add(session);
                }
                return session;
            }), phaseChangeHandler);
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
}