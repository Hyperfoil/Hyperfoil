package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.MixStrategy;
import io.sailrocket.api.Report;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.impl.statistics.ReportStatisticsCollector;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

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

    private ArrayList<Worker> workers;

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

        //Create Simulation threadpool and worker pools
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        workers = new ArrayList(threads);
        IntStream.range(0, threads).forEach(i -> workers.add(i, new WorkerImpl(pacerRate, exec, clientPool)));
        List<CompletableFuture<Void>> completedFutures = new ArrayList<>(threads);

        //TODO:: this needs to be a worker queue
        //TODO:: need to call back to an actual sequence selector
        workers.forEach(worker -> completedFutures.add(worker.runSlot(duration, () -> scenarios.stream().findFirst().get().firstSequence())));

        this.statisticsConsumer = new ReportStatisticsCollector(
                tags,
                rate,
                duration,
                startTime
        );


        for (CompletableFuture<Void> result : completedFutures) {
            result.get();
        }
        exec.shutdown();
        collateStatistics();
        return this.statisticsConsumer.reports();
    }

   /**
     * Print details on console.
     */
    public void printDetails(Consumer<SequenceStatistics> printStatsConsumer) {

        scenarios.forEach(scenario -> {
            scenario.sequences().forEach(sequence -> printStatsConsumer.accept(sequence.statistics()));
        });

    }


    private void collateStatistics() {
        done = true;

        if (statisticsConsumer != null) {
            scenarios.forEach(scenario -> {
                scenario.sequences().forEach(sequence -> statisticsConsumer.accept(sequence.statistics()));
            });
        }

    }

}