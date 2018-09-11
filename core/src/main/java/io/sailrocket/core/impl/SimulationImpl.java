package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Report;
import io.sailrocket.api.Session;
import io.sailrocket.api.Statistics;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.impl.statistics.ReportStatisticsCollector;
import io.sailrocket.core.session.SessionFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationImpl implements Simulation {
    private static final Logger log = LoggerFactory.getLogger(SimulationImpl.class);

    private final Collection<Phase> phases;
    private final JsonObject tags;

    private HttpClientPool clientPool;
    private Collection<Session> sessions = new ArrayList<>();

    private long startTime;
    private long nextPhaseStart;
    private long nextPhaseFinish;
    private long nextPhaseTerminate;

    public SimulationImpl(HttpClientPoolFactory clientBuilder, Collection<Phase> phases, JsonObject tags) {
        this.phases = phases;
        this.tags = tags;
        HttpClientPool httpClientPool = null;
        try {
            httpClientPool = clientBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.clientPool = httpClientPool;
    }

    public JsonObject tags() {
        return tags;
    }

    public void shutdown() {
        clientPool.shutdown();
    }

    public Collection<Report> run() throws Exception {
        //Initialise HttpClientPool
        CountDownLatch latch = new CountDownLatch(1);
        clientPool.start(v1 -> {
            latch.countDown();
        });

        Lock statusLock = new ReentrantLock();
        Condition statusCondition = statusLock.newCondition();
        for (Phase phase : phases) {
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

        long now = System.currentTimeMillis();
        this.startTime = now;
        do {
            now = System.currentTimeMillis();
            for (Phase phase : phases) {
                if (phase.status() == Phase.Status.RUNNING && phase.absoluteStartTime() + phase.duration() <= now) {
                    phase.finish();
                }
                if (phase.status() == Phase.Status.FINISHED && phase.maxDuration() >= 0 && phase.absoluteStartTime() + phase.maxDuration() <= now) {
                    phase.terminate();
                }
            }
            Phase[] availablePhases = getAvailablePhases();
            for (Phase phase : availablePhases) {
                phase.start(clientPool);
            }
            nextPhaseStart = phases.stream()
                  .filter(phase -> phase.status() == Phase.Status.NOT_STARTED && phase.startTime() >= 0)
                  .mapToLong(phase -> this.startTime + phase.startTime()).min().orElse(Long.MAX_VALUE);
            nextPhaseFinish = phases.stream()
                  .filter(phase -> phase.status() == Phase.Status.RUNNING)
                  .mapToLong(phase -> phase.absoluteStartTime() + phase.duration()).min().orElse(Long.MAX_VALUE);
            nextPhaseTerminate = phases.stream()
                  .filter(phase -> (phase.status() == Phase.Status.RUNNING || phase.status() == Phase.Status.FINISHED) && phase.maxDuration() >= 0)
                  .mapToLong(phase -> phase.absoluteStartTime() + phase.maxDuration()).min().orElse(Long.MAX_VALUE);
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
        } while (phases.stream().anyMatch(phase -> phase.status() != Phase.Status.TERMINATED));

        ReportStatisticsCollector statisticsConsumer = new ReportStatisticsCollector(tags, 0, 0, startTime);
        visitStatistics(statisticsConsumer);
        return statisticsConsumer.reports();
    }

    private Phase[] getAvailablePhases() {
        return phases.stream().filter(phase -> phase.status() == Phase.Status.NOT_STARTED &&
            startTime + phase.startTime() <= System.currentTimeMillis() &&
            phase.startAfter().stream().allMatch(dep -> dep.status().isFinished()) &&
            phase.startAfterStrict().stream().allMatch(dep -> dep.status() == Phase.Status.TERMINATED))
              .toArray(Phase[]::new);
    }

    public void visitStatistics(Consumer<Statistics> consumer) {
        synchronized (sessions) {
            for (Session session : sessions) {
                for (Statistics statistics : session.statistics()) {
                    consumer.accept(statistics);
                }
            }
        }
    }

    @Override
    public Collection<Phase> phases() {
        return phases;
    }

}