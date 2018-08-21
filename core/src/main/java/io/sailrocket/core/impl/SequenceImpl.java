package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SequenceImpl implements Sequence {

    private SequenceStatistics statistics = new SequenceStatistics();
    private SequenceContext sequenceContext;

    //TODO:: think about branching
    private List<AsyncStep> steps = new ArrayList<>();

    @Override
    public Sequence step(Step step) {
        this.steps.add((AsyncStep) step);
        return this;
    }

    @Override
    public List<AsyncStep> getSteps() {
        return steps;
    }

    @Override
    public SequenceStatistics statistics() {
        return statistics;
    }

    public void context(SequenceContext sequenceContext) {
        this.sequenceContext = sequenceContext;
        this.sequenceContext.sequenceStats(this.statistics);
    }

    public SequenceContext context() {
        return sequenceContext;
    }

//    public StepImpl rootStep() {
//        return head;
//    }

    public CompletableFuture<SequenceContext> buildSequenceFuture() {
        Iterator<AsyncStep> iterator = steps.iterator();
        if (!iterator.hasNext()) {
            return CompletableFuture.completedFuture(this.sequenceContext);
        }
        CompletableFuture<SequenceContext> chainedFuture = iterator.next().asyncExec(this.sequenceContext);
        while (iterator.hasNext()) {
            AsyncStep step = iterator.next();
            chainedFuture = chainedFuture.thenCompose(step::asyncExec);
        }
        return chainedFuture;
    }
}
