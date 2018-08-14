package io.sailrocket.core.client;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.MixStrategy;
import io.sailrocket.api.Scenario;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.SimulationContext;
import io.sailrocket.core.util.Report;
import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    private final JsonObject tags;

    private volatile long startTime;
    private volatile boolean done;

    private Map<Scenario, SimulationContext> scenarios = new ConcurrentHashMap<>();
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
        scenarios.put(scenario, new SimulationContext(tags));
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

    public List<Report> run() throws Exception {
        //Initialise HttpClientPool
        CountDownLatch latch = new CountDownLatch(1);
        clientPool.start(v1 -> {
            latch.countDown();
        });
        try {
            latch.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }

        //Create Simulation threadpool and worker pools
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        workers = new ArrayList(threads);
        IntStream.range(0, threads).forEach(i -> workers.add(i, new WorkerImpl(pacerRate, exec, clientPool)));
        List<CompletableFuture<Void>> completedFutures = new ArrayList<>(threads);

        //TODO:: this needs to be a worker queue
        //TODO:: need to call back to an actual sequence selector
        workers.forEach(worker -> completedFutures.add(worker.runSlot(duration, () -> scenarios.keySet().stream().findFirst().get().firstSequence())));

        for (CompletableFuture<Void> result : completedFutures) {
            result.get();
        }
        exec.shutdown();
        return end();
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
//        double progress = (100 * (System.nanoTime() - startTime)) / (double) duration;
//        System.out.format("progress: %.2f%% done - total requests/responses %d/%d, ratio %.2f, read %d kb/s, written %d kb/s, inflight= %d%n",
//                progress,
//                workerStats.requestCount.intValue(),
//                workerStats.responseCount.intValue(),
//                ratio(), readThroughput(), writeThroughput(),
//                requestContext.sequenceContext.clientPool().inflight());
    }


    private List<Report> end() {
        done = true;

        List<Report> reports = scenarios.values().stream()
                .map(context -> {
                    long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(duration);
                    long elapsed = System.nanoTime() - startTime;
                    Histogram cp = context.sequenceStats().histogram.copy();
                    cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
                    cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
                    context.report().measures(
                            expectedRequests,
                            elapsed,
                            cp,
                            context.sequenceStats().responseCount.intValue(),
                            ratio(context.sequenceStats()),
                            context.sequenceStats().connectFailureCount.intValue(),
                            context.sequenceStats().resetCount.intValue(),
                            context.sequenceStats().resetCount.intValue(),
                            Stream.of(context.sequenceStats().statuses).mapToInt(LongAdder::intValue).toArray(),
                            clientPool.bytesRead(),
                            clientPool.bytesWritten()
                    );
                    return context.report();
                }).collect(Collectors.toList());
        clientPool.shutdown();

        return reports;
    }

    private double ratio(SequenceStats sequenceStats) {
        long end = Math.min(System.nanoTime(), startTime + duration);
        long expected = rate * (end - startTime) / 1000000000;
        return sequenceStats.requestCount.doubleValue() / (double) expected;
    }
}