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
//    private ScheduledSequence head;
//    private ScheduledSequence tail;

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
                pacer.setIntendedEndTime(slotEnds);
                doSequenceInSlot(pacer, sequenceSupplier, doneHandler);
            }
        });
    }

    private void doSequenceInSlot(Pacer pacer, Supplier<Sequence> sequenceSupplier, Consumer<Void> doneHandler) {
        long now = System.nanoTime();
        if (now > pacer.getIntendedEndTime()) {
            doneHandler.accept(null);
            return;
        }
        //TODO:: Call back to simulation to get next sequenceContext
        SequenceContextImpl sequenceContext = new SequenceContextImpl(sequenceSupplier.get(), this, pacer, now);

        CompletableFuture<SequenceContext> sequenceFuture = ((SequenceImpl) sequenceContext.sequence()).buildSequenceFuture(this, sequenceContext);
        sequenceFuture.whenComplete((ctx, t) -> {
            long endTime = System.nanoTime();
            long durationMillis = endTime - sequenceContext.getStartTime();
            //TODO:: this needs to be asnyc to histogram verticle - we should be able to process various composite stats in realtime
            sequenceContext.sequenceStats().histogram.recordValue(durationMillis);
            if (t != null) {
               t.printStackTrace();
            }
            sequenceContext.pacer().acquire(1);
            doSequenceInSlot(sequenceContext.pacer(), sequenceSupplier, doneHandler);
        });
    }
}

