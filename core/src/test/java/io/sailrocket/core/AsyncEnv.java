package io.sailrocket.core;

import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.RequestContext;
import io.sailrocket.core.client.Worker;
import io.sailrocket.core.client.WorkerStats;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@RunWith(VertxUnitRunner.class)
public abstract class AsyncEnv {

    protected static final int ASYNC_THREADS = 4;

    protected int rate = 1000;
    protected int pacerRate = rate / ASYNC_THREADS;
    protected final long DURATION = TimeUnit.SECONDS.toNanos(30);

    protected final ExecutorService exec = Executors.newFixedThreadPool(ASYNC_THREADS);
    protected final WorkerStats workerStats = new WorkerStats();

    ArrayList<Worker> workers;

    protected CountDownLatch runLatch = new CountDownLatch(1);

    public AsyncEnv() {
        workers = new ArrayList(ASYNC_THREADS);
    }

    public void run(RequestContext requestContext) throws ExecutionException, InterruptedException {
        IntStream.range(0, ASYNC_THREADS).forEach(i -> workers.add(i, new Worker(requestContext, workerStats, pacerRate, exec)));
        List<CompletableFuture<Void>> results = new ArrayList<>(ASYNC_THREADS);
        workers.forEach(worker -> results.add(worker.runSlot(DURATION)));
        for (CompletableFuture<Void> result : results) {
            result.get();
        }
        exec.shutdown();
        runLatch.countDown();
    }


    public int requestCount(){
        return workerStats.requestCount.intValue();
    }

//    private Report end(int requestCount) {
//        long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(workerStats.duration);
//        long elapsed = System.nanoTime() - startTime;
//        Histogram cp = workerStats.histogram.copy();
//        cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
//        cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
//        report.measures(
//                expectedRequests,
//                elapsed,
//                cp,
//                workerStats.responseCount.intValue(),
//                ratio(),
//                workerStats.connectFailureCount.intValue(),
//                workerStats.resetCount.intValue(),
//                requestCount,
//                Stream.of(workerStats.statuses).mapToInt(LongAdder::intValue).toArray(),
//                requestContext.client.bytesRead(),
//                requestContext.client.bytesWritten()
//        );
//        requestContext.client.shutdown();
//        return report;
//    }


    protected volatile int count;
    private Vertx vertx;
    protected HttpClientProvider provider;

    @Before
    public void before(TestContext ctx) {
        count = 0;
        vertx = Vertx.vertx();
        vertx.createHttpServer().requestHandler(req -> {
            count++;
            req.response().end();
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());
    }
}
