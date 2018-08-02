package io.sailrocket.core.client;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.api.HttpResponse;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.HttpResponseImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class WorkerImpl implements Worker {

    private final Executor exec;
    private SimulationImpl.ScheduledRequest head;
    private SimulationImpl.ScheduledRequest tail;

    private int pacerRate;
    private WorkerStats workerStats;

    public WorkerImpl(WorkerStats workerStats, int pacerRate, Executor exec) {
        this.exec = exec;
        this.pacerRate = pacerRate;
        this.workerStats = workerStats;
    }

    public WorkerImpl(WorkerStats workerStats, int pacerRate) {
        this(workerStats, pacerRate, Runnable::run);
    }


    @Override
    public CompletableFuture<HttpResponse> runSlot(long duration, RequestContext requestContext) {
        CompletableFuture<HttpResponse> result = new CompletableFuture<>();
        runSlot(duration, result::complete, requestContext);
        return result;
    }

    private void runSlot(long duration, Consumer<HttpResponse> doneHandler, RequestContext requestContext) {
        exec.execute(() -> {
            if (duration > 0) {
                long slotBegins = System.nanoTime();
                long slotEnds = slotBegins + duration;

                //TODO:: define pluggable "Pacing strategies"
                //This would allow us to configure different benchmark behaviour when the system under test is saturated
                Pacer pacer = new Pacer(pacerRate);
                pacer.setInitialStartTime(slotBegins);
                doRequestInSlot(pacer, slotEnds, requestContext);
                if (doneHandler != null) {
                    doneHandler.accept(new HttpResponseImpl()); //TODO:: put HttpResponse here
                }
            }
        });
    }

    private void doRequestInSlot(Pacer pacer, long slotEnds, RequestContext requestContext) {
        while (true) {
            long now = System.nanoTime();
            if (now > slotEnds) {
                return;
            } else {
                SimulationImpl.ScheduledRequest schedule = new SimulationImpl.ScheduledRequest(now);
                if (head == null) {
                    head = tail = schedule;
                } else {
                    tail.next = schedule;
                    tail = schedule;
                }
                checkPending(requestContext);
                pacer.acquire(1);
            }
        }
    }

    private void checkPending(RequestContext requestContext) {
        HttpRequest conn;
        while (head != null && (conn = requestContext.sequenceContext.clientPool().request(requestContext.payload != null ? HttpMethod.POST : HttpMethod.GET, requestContext.path)) != null) {
            long startTime = head.startTime;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            doRequest(conn, startTime,requestContext);
        }
    }

    private void doRequest(HttpRequest request, long startTime, RequestContext requestContext) {
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

