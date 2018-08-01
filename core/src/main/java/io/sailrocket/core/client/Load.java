package io.sailrocket.core.client;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpClient;
import io.sailrocket.core.api.HttpResponse;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.ClientSessionImpl;
import io.sailrocket.core.util.Report;
import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Load {

    private final int threads;
    private final int rate;
    private final int pacerRate;
    private final long duration;
    private final long warmup;
    private final Report report;

    private final RequestContext requestContext;

    private volatile WorkerStats workerStats = new WorkerStats();

    private volatile long startTime;
    private volatile boolean done;


    public Load(int threads, int rate, long duration, long warmup,
                HttpClientBuilder clientBuilder, String path, ByteBuf payload,
                Report report) {
        this.threads = threads;
        this.rate = rate;
        this.pacerRate = rate / threads;
        this.duration = duration;
        this.warmup = warmup;
        this.report = report;
        HttpClient httpClient = null;
        try {
            httpClient = clientBuilder.build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.requestContext = new RequestContext(new ClientSessionImpl(httpClient, null), path, payload);
    }

    Report run() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        requestContext.sequenceContext.client().start(v1 -> {
            latch.countDown();
        });
        try {
            latch.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        System.out.println("connection(s) created...");
        if (warmup > 0) {
            System.out.println("warming up...");
        }
        new WorkerImpl(workerStats, pacerRate).runSlot(warmup, requestContext);
        System.out.println("starting rate=" + rate);
        startTime = System.nanoTime();
        workerStats.requestCount.reset();
        workerStats.responseCount.reset();
        requestContext.sequenceContext.client().resetStatistics();
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        ArrayList<Worker> workers = new ArrayList(threads);
        IntStream.range(0, threads).forEach(i -> workers.add(i, new WorkerImpl(workerStats, pacerRate, exec)));
        List<CompletableFuture<HttpResponse>> results = new ArrayList<>(threads);
        workers.forEach(worker -> results.add(worker.runSlot(duration, this.requestContext)));
        for (CompletableFuture<HttpResponse> result : results) {
            result.get();
        }
        exec.shutdown();
        return end(workerStats.requestCount.intValue());
    }

    private double ratio() {
        long end = Math.min(System.nanoTime(), startTime + duration);
        long expected = rate * (end - startTime) / 1000000000;
        return workerStats.requestCount.doubleValue() / (double) expected;
    }

    private long readThroughput() {
        return requestContext.sequenceContext.client().bytesRead() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime)) * 1024);
    }

    private long writeThroughput() {
        return requestContext.sequenceContext.client().bytesWritten() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime) * 1024));
    }

    /**
     * Print details on console.
     */
    public void printDetails() {
        double progress = (100 * (System.nanoTime() - startTime)) / (double) duration;
        System.out.format("progress: %.2f%% done - total requests/responses %d/%d, ratio %.2f, read %d kb/s, written %d kb/s, inflight= %d%n",
                progress,
                workerStats.requestCount.intValue(),
                workerStats.responseCount.intValue(),
                ratio(), readThroughput(), writeThroughput(),
                requestContext.sequenceContext.client().inflight());
    }

    static class ScheduledRequest {

        final long startTime;
        ScheduledRequest next;

        public ScheduledRequest(long startTime) {
            this.startTime = startTime;
        }
    }


    private Report end(int requestCount) {
        done = true;
        long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(duration);
        long elapsed = System.nanoTime() - startTime;
        Histogram cp = workerStats.histogram.copy();
        cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
        cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        report.measures(
                expectedRequests,
                elapsed,
                cp,
                workerStats.responseCount.intValue(),
                ratio(),
                workerStats.connectFailureCount.intValue(),
                workerStats.resetCount.intValue(),
                requestCount,
                Stream.of(workerStats.statuses).mapToInt(LongAdder::intValue).toArray(),
                requestContext.sequenceContext.client().bytesRead(),
                requestContext.sequenceContext.client().bytesWritten()
        );
        requestContext.sequenceContext.client().shutdown();
        return report;
    }
}