package io.sailrocket.core.client;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class Worker {

    private final Executor exec;
    private Load.ScheduledRequest head;
    private Load.ScheduledRequest tail;

    private int pacerRate;
    private WorkerStats workerStats;
    private RequestContext requestContext;

    public Worker(RequestContext requestContext, WorkerStats workerStats, int pacerRate, Executor exec) {
        this.exec = exec;
        this.pacerRate = pacerRate;
        this.workerStats = workerStats;
        this.requestContext = requestContext;
    }

    public Worker(RequestContext requestContext, WorkerStats workerStats, int pacerRate) {
        this(requestContext, workerStats, pacerRate, Runnable::run);
    }


    public CompletableFuture<Void> runSlot(long duration) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        runSlot(duration, result::complete);
        return result;
    }

    private void runSlot(long duration, Consumer<Void> doneHandler) {
        exec.execute(() -> {
            if (duration > 0) {
                long slotBegins = System.nanoTime();
                long slotEnds = slotBegins + duration;

                //TODO:: define pluggable "Pacing strategies"
                //This would allow us to configure different benchmark behaviour when the system under test is saturated
                Pacer pacer = new Pacer(pacerRate);
                pacer.setInitialStartTime(slotBegins);
                doRequestInSlot(pacer, slotEnds);
                if (doneHandler != null) {
                    doneHandler.accept(null);
                }
            }
        });
    }

    private void doRequestInSlot(Pacer pacer, long slotEnds) {
        while (true) {
            long now = System.nanoTime();
            if (now > slotEnds) {
                return;
            } else {
                Load.ScheduledRequest schedule = new Load.ScheduledRequest(now);
                if (head == null) {
                    head = tail = schedule;
                } else {
                    tail.next = schedule;
                    tail = schedule;
                }
                checkPending();
                pacer.acquire(1);
            }
        }
    }

    private void checkPending() {
        HttpRequest conn;
        while (head != null && (conn = requestContext.client.request(requestContext.payload != null ? HttpMethod.POST : HttpMethod.GET, requestContext.path)) != null) {
            long startTime = head.startTime;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            doRequest(conn, startTime);
        }
    }

    private void doRequest(HttpRequest request, long startTime) {
        workerStats.requestCount.increment();
        if (requestContext.payload != null) {
            request.putHeader("content-length", "" + requestContext.payload.readableBytes());
        }
        request.statusHandler(code -> {
            int status = (code - 200) / 100;
            if (status >= 0 && status < workerStats.statuses.length) {
                workerStats.statuses[status].increment();
            }
        }).resetHandler(frame -> {
            workerStats.resetCount.increment();
        }).endHandler(v -> {
            workerStats.responseCount.increment();
            long endTime = System.nanoTime();
            long durationMillis = endTime - startTime;
            //TODO:: this needs to be asnyc to histogram verticle - we should be able to process various composite stats in realtime
            workerStats.histogram.recordValue(durationMillis);
//          checkPending();
        });
        if (requestContext.payload != null) {
            request.end(requestContext.payload.duplicate());
        } else {
            request.end();
        }
    }
}

