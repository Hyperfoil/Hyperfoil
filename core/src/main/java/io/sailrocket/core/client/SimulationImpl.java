package io.sailrocket.core.client;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.MixStrategy;
import io.sailrocket.api.Report;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.ReportStatisticsCollector;
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

    private Consumer<SequenceStatistics> statisticsConsumer;

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

    @Override
    public Simulation statisticsCollector(Consumer<SequenceStatistics> statisticsConsumer) {
        this.statisticsConsumer = statisticsConsumer;
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

        ReportStatisticsCollector reportStatisticsCollector = new ReportStatisticsCollector(
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
        return reportStatisticsCollector.reports();
    }

/*

    private long readThroughput() {
        return clientPool.bytesRead() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime)) * 1024);
    }

    private long writeThroughput() {
        return clientPool.bytesWritten() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime) * 1024));
    }*/

    /**
     * Print details on console.
     */
    public void printDetails() {

        //TODO:: print progress
        Consumer<SequenceStatistics> printStatsConsumer = ((statistics) -> {
            System.out.format("%s : total requests/responses %d/%d, max %.2f, min %.2f, mean %.2f",
//                    statistics.name,
                    statistics.requestCount,
                    statistics.histogram.getMaxValue(),
                    statistics.histogram.getMinValue(),
                    statistics.histogram.getMean()
            );
        });

        scenarios.forEach(scenario -> {
            scenario.sequences().forEach(sequence -> printStatsConsumer.accept(sequence.statistics()));
        });

//        double progress = (100 * (System.nanoTime() - startTime)) / (double) duration;
//        System.out.format("progress: %.2f%% done - total requests/responses %d/%d, ratio %.2f, read %d kb/s, written %d kb/s, inflight= %d%n",
//                progress,
//                workerStats.requestCount.intValue(),
//                workerStats.responseCount.intValue(),
//                ratio(), readThroughput(), writeThroughput(),
//                requestContext.sequenceContext.clientPool().inflight());
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