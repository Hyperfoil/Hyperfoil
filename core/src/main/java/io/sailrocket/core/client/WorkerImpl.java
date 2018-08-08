package io.sailrocket.core.client;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Sequence;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.impl.SequenceContextImpl;
import io.sailrocket.core.impl.SequenceImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WorkerImpl implements Worker {

    private final Executor exec;
    private ScheduledSequence head;
    private ScheduledSequence tail;

    private HttpClientPool clientPool;

    private int pacerRate;

    public WorkerImpl(int pacerRate, Executor exec,  HttpClientPool clientPool) {
        this.exec = exec;
        this.pacerRate = pacerRate;
        this.clientPool = clientPool;
    }

    public WorkerImpl(int pacerRate,  HttpClientPool clientPool) {
        this(pacerRate, Runnable::run, clientPool);
    }


    @Override
    public HttpClientPool clientPool() {
        return clientPool;
    }

    @Override
    public CompletableFuture<Void> runSlot(long duration, Supplier<Sequence> sequenceSupplier) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        runSlot(duration, completableFuture::complete, sequenceSupplier);
        return completableFuture;
    }

    private void runSlot(long duration, Consumer<Void> doneHandler, Supplier<Sequence> sequenceSupplier) {
        exec.execute(() -> {
            if (duration > 0) {
                long slotBegins = System.nanoTime();
                long slotEnds = slotBegins + duration;

                //TODO:: define pluggable "Pacing strategies"
                //This would allow us to configure different benchmark behaviour when the system under test is saturated
                Pacer pacer = new Pacer(pacerRate);
                pacer.setInitialStartTime(slotBegins);
                doSequenceInSlot(pacer, slotEnds, sequenceSupplier);
                if (doneHandler != null) {
                    doneHandler.accept(null);
                }
            }
        });
    }

    private void doSequenceInSlot(Pacer pacer, long slotEnds, Supplier<Sequence> sequenceSupplier) {

        //This run until the slot ends - this is the current state runtime
        while (true) {
            long now = System.nanoTime();
            if (now > slotEnds) {
                return;
            } else {
                //TODO:: Call back to simulation to get next sequenceContext
                ScheduledSequence schedule = new ScheduledSequence(now, new SequenceContextImpl(sequenceSupplier.get(), this));
                if (head == null) {
                    head = tail = schedule;
                } else {
                    tail.next = schedule;
                    tail = schedule;
                }
                checkPendingSequences();
                pacer.acquire(1);
            }
        }
    }

    private void checkPendingSequences() {
        while (head != null) {
            ScheduledSequence scheduledSequence = head;
            long startTime = head.startTime;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            runSequence(startTime, scheduledSequence.sequenceContext());
        }
    }

    /*
     * This runs a sequenceContext in a slot, sequences are not a 1-to-1 mapping to a request
     * the request needs to be executed in the step, and the results passed to the next step, so
     * */
    private void runSequence(long startTime, SequenceContext sequenceContext) {

        sequenceContext.sequenceStats().requestCount.increment();

        CompletableFuture<SequenceContext> sequenceFuture = ((SequenceImpl) sequenceContext.sequence()).buildSequenceFuture(this);

        //TODO:: this shouldn't be blocking - need to make sure this doesn't block the worker thread
        if (sequenceFuture.complete(sequenceContext)){
            long endTime = System.nanoTime();
            long durationMillis = endTime - startTime;
            //TODO:: this needs to be asnyc to histogram verticle - we should be able to process various composite stats in realtime
            sequenceContext.sequenceStats().histogram.recordValue(durationMillis);

        }
        else { //TODO:: think about how failures are handled, and whether they impact stats
            System.out.println("Sequence future did not complete");
        }

    }
}

