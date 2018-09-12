package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Report;
import io.sailrocket.api.Session;
import io.sailrocket.core.api.SimulationRunner;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.impl.statistics.ReportStatisticsCollector;
import io.sailrocket.core.session.SessionFactory;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationRunnerImpl implements SimulationRunner {
    private static final Logger log = LoggerFactory.getLogger(SimulationRunnerImpl.class);

    private final Simulation simulation;
    private final Map<String, PhaseInstance> instances = new HashMap<>();
    private final ReentrantLock statusLock = new ReentrantLock();
    private final Condition statusCondition = statusLock.newCondition();

    private HttpClientPool clientPool;
    private List<Session> sessions = new ArrayList<>();

    private long startTime;
    private long nextPhaseStart;
    private long nextPhaseFinish;
    private long nextPhaseTerminate;

    public SimulationRunnerImpl(HttpClientPoolFactory clientBuilder, Simulation simulation) {
        this.simulation = simulation;
        HttpClientPool httpClientPool = null;
        try {
            httpClientPool = clientBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.clientPool = httpClientPool;
    }

    @Override
    public void init() throws Exception {
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
            }), statusLock, statusCondition);
            phase.reserveSessions();
        }

        try {
            latch.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Report> run() throws Exception {
        long now = System.currentTimeMillis();
        this.startTime = now;
        do {
            now = System.currentTimeMillis();
            for (PhaseInstance phase : instances.values()) {
                if (phase.status() == PhaseInstance.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
                    phase.finish();
                }
                if (phase.status() == PhaseInstance.Status.FINISHED && phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
                    phase.terminate();
                }
            }
            PhaseInstance[] availablePhases = getAvailablePhases();
            for (PhaseInstance phase : availablePhases) {
                phase.start(clientPool);
            }
            nextPhaseStart = instances.values().stream()
                  .filter(phase -> phase.status() == PhaseInstance.Status.NOT_STARTED && phase.definition().startTime() >= 0)
                  .mapToLong(phase -> this.startTime + phase.definition().startTime()).min().orElse(Long.MAX_VALUE);
            nextPhaseFinish = instances.values().stream()
                  .filter(phase -> phase.status() == PhaseInstance.Status.RUNNING)
                  .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().duration()).min().orElse(Long.MAX_VALUE);
            nextPhaseTerminate = instances.values().stream()
                  .filter(phase -> (phase.status() == PhaseInstance.Status.RUNNING || phase.status() == PhaseInstance.Status.FINISHED) && phase.definition().maxDuration() >= 0)
                  .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().maxDuration()).min().orElse(Long.MAX_VALUE);
            long delay = Math.min(Math.min(nextPhaseStart, nextPhaseFinish), nextPhaseTerminate) - System.currentTimeMillis();

            delay = Math.min(delay, 1000);
            log.debug("Wait {} ms", delay);
            if (delay > 0) {
                statusLock.lock();
                try {
                    statusCondition.await(delay, TimeUnit.MILLISECONDS);
                } finally {
                    statusLock.unlock();
                }
            }
        } while (instances.values().stream().anyMatch(phase -> phase.status() != PhaseInstance.Status.TERMINATED));

        ReportStatisticsCollector statisticsConsumer = new ReportStatisticsCollector(simulation);
        visitSessions(statisticsConsumer);
        return statisticsConsumer.reports();
    }

    private PhaseInstance[] getAvailablePhases() {
        return instances.values().stream().filter(phase -> phase.status() == PhaseInstance.Status.NOT_STARTED &&
            startTime + phase.definition().startTime() <= System.currentTimeMillis() &&
            phase.definition().startAfter().stream().allMatch(dep -> instances.get(dep).status().isFinished()) &&
            phase.definition().startAfterStrict().stream().allMatch(dep -> instances.get(dep).status() == PhaseInstance.Status.TERMINATED))
              .toArray(PhaseInstance[]::new);
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
}