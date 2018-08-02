package io.sailrocket.core;

import io.sailrocket.core.api.HttpResponse;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.RequestContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.client.WorkerImpl;
import io.sailrocket.core.client.SequenceStats;
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
    protected final SequenceStats sequenceStats = new SequenceStats();

    ArrayList<Worker> workers;

    protected CountDownLatch runLatch = new CountDownLatch(1);

    public AsyncEnv() {
        workers = new ArrayList<>(ASYNC_THREADS);
    }

    public void run(RequestContext requestContext) throws ExecutionException, InterruptedException {
        IntStream.range(0, ASYNC_THREADS).forEach(i -> workers.add(i, new WorkerImpl(sequenceStats, pacerRate, exec)));
        List<CompletableFuture<HttpResponse>> results = new ArrayList<>(ASYNC_THREADS);
        workers.forEach(worker -> results.add(worker.runSlot(DURATION, requestContext)));
        for (CompletableFuture<HttpResponse> result : results) {
            result.get();
        }
        exec.shutdown();
        runLatch.countDown();
    }


    public int requestCount(){
        return sequenceStats.requestCount.intValue();
    }

//    private Report end(int requestCount) {
//        long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(sequenceStats.duration);
//        long elapsed = System.nanoTime() - startTime;
//        Histogram cp = sequenceStats.histogram.copy();
//        cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
//        cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
//        report.measures(
//                expectedRequests,
//                elapsed,
//                cp,
//                sequenceStats.responseCount.intValue(),
//                ratio(),
//                sequenceStats.connectFailureCount.intValue(),
//                sequenceStats.resetCount.intValue(),
//                requestCount,
//                Stream.of(sequenceStats.statuses).mapToInt(LongAdder::intValue).toArray(),
//                requestContext.clientPool.bytesRead(),
//                requestContext.clientPool.bytesWritten()
//        );
//        requestContext.clientPool.shutdown();
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
