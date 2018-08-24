package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.MixStrategy;
import io.sailrocket.api.Report;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.Session;
import io.sailrocket.api.Statistics;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.impl.statistics.ReportStatisticsCollector;
import io.sailrocket.core.session.SessionFactory;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:johara@redhat.com">John O'Hara</a>
 */
public class SimulationImpl implements Simulation {

    private final int threads;
    private final int rate;
    private final int pacerRate;
    private final long duration;
    private final long warmup;

    private ReportStatisticsCollector statisticsConsumer;

    private final JsonObject tags;

    private volatile long startTime;
    private volatile boolean done;

    private List<Scenario> scenarios = new ArrayList<>();
    private List<MixStrategy> mixStrategies = new ArrayList<>();

    private ArrayList<Session> sessions;

    private HttpClientPool clientPool;

    public SimulationImpl(int threads, int rate, long duration, long warmup,
                          HttpClientPoolFactory clientBuilder, JsonObject tags) {
        this.threads = threads;
        this.rate = rate;
        this.pacerRate = rate / threads;
        this.duration = duration;
        this.warmup = warmup;
        this.tags = tags;
        HttpClientPool httpClientPool = null;
        try {
            httpClientPool = clientBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.clientPool = httpClientPool;
    }


    @Override
    public Simulation scenario(Scenario scenario) {
        scenarios.add(scenario);
        return this;
    }

    @Override
    public Simulation mixStrategy(MixStrategy mixStrategy) {
        mixStrategies.add(mixStrategy);
        return this;
    }

    public JsonObject tags() {
        return tags;
    }

    public int rate() {
        return rate;
    }

    public long duration() {
        return duration;
    }

    public int numOfScenarios() {
        return scenarios.size();
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
        try {
            latch.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        Scenario scenario = scenarios.stream().findFirst().get();
        sessions = new ArrayList(threads);
        long deadline = System.currentTimeMillis() + TimeUnit.NANOSECONDS.toMillis(duration);
        for (int i = 0; i < threads; ++i) {
            // TODO: the concurrency must be set in the scenario
            Session session = SessionFactory.create(clientPool, 16, 16, () -> System.currentTimeMillis() >= deadline, scenario);
            sessions.add(session);
        }
        for (Session session : sessions) {
            session.proceed();
        }
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(duration));

        this.statisticsConsumer = new ReportStatisticsCollector(
                tags,
                rate,
                duration,
                startTime
        );


        collateStatistics();
        return this.statisticsConsumer.reports();
    }

   /**
     * Print details on console.
     */
    public void printDetails(Consumer<Statistics> printStatsConsumer) {

        sessions.forEach(session -> printStatsConsumer.accept(session.statistics()));

    }


    private void collateStatistics() {
        done = true;

        if (statisticsConsumer != null) {
            for (Session session : sessions) {
                statisticsConsumer.accept(session.statistics());
            }
        }

    }

}