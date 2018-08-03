package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SequenceImpl implements Sequence {

    //TODO:: think about branching
    private List<AsyncStep> steps = new ArrayList<>();

    private StepImpl head = null;

    @Override
    public Sequence step(Step step) {
        this.steps.add((AsyncStep) step);
        if (head == null)
            head = (StepImpl) step;
        return this;
    }

    @Override
    public List<AsyncStep> getSteps() {
        return steps;
    }

    public StepImpl rootStep() {
        return head;
    }

    public CompletableFuture<SequenceContext> buildSequenceFuture(Worker worker, HttpClientPool clientPool) {

        CompletableFuture<SequenceContext> rootFuture = new CompletableFuture().supplyAsync(() ->
                new SequenceContextImpl(this, worker)
        );

        return steps.stream()
                .reduce(rootFuture
                        , (sequenceFuture, step) -> sequenceFuture.thenCompose(sequenceState -> step.asyncExec(sequenceState))
                        , (sequenceFuture, e) -> sequenceFuture
                );
    }
}
