package io.sailrocket.core.client;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.api.HttpResponse;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.HttpResponseImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class WorkerImpl implements Worker {

    private final Executor exec;
    private ScheduledSequence head;
    private ScheduledSequence tail;

    private int pacerRate;

    public WorkerImpl(int pacerRate, Executor exec) {
        this.exec = exec;
        this.pacerRate = pacerRate;
    }

    public WorkerImpl(int pacerRate) {
        this(pacerRate, Runnable::run);
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

    private void doRequestInSlot(Pacer pacer, long slotEnds, SequenceContext sequenceContext) {
        while (true) {
            long now = System.nanoTime();
            if (now > slotEnds) {
                return;
            } else {
                ScheduledSequence schedule = new ScheduledSequence(now, sequenceContext.sequence());
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

    private void checkPending(SequenceContext sequenceContext) {
        HttpRequest conn;
        while (head != null && (conn = sequenceContext.clientPool().request(sequenceContext.sequence().rootStep(). != null ? HttpMethod.POST : HttpMethod.GET, sequenceContext.sequence().rootStep().getEndpoint())) != null) {
            long startTime = head.startTime;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            doRequest(conn, startTime,requestContext);
        }
    }

    private void doRequest(HttpRequest request, long startTime, RequestContext requestContext) {
        requestContext.sequenceContext.sequenceStats().requestCount.increment();
        if (requestContext.payload != null) {
            request.putHeader("content-length", "" + requestContext.payload.readableBytes());
        }
        request.statusHandler(code -> {
            int status = (code - 200) / 100;
            if (status >= 0 && status < requestContext.sequenceContext.sequenceStats().statuses.length) {
                requestContext.sequenceContext.sequenceStats().statuses[status].increment();
            }
        }).resetHandler(frame -> {
            requestContext.sequenceContext.sequenceStats().resetCount.increment();
        }).endHandler(v -> {
            requestContext.sequenceContext.sequenceStats().responseCount.increment();
            long endTime = System.nanoTime();
            long durationMillis = endTime - startTime;
            //TODO:: this needs to be asnyc to histogram verticle - we should be able to process various composite stats in realtime
            requestContext.sequenceContext.sequenceStats().histogram.recordValue(durationMillis);
//          checkPending();
        });
        if (requestContext.payload != null) {
            request.end(requestContext.payload.duplicate());
        } else {
            request.end();
        }
    }
}

