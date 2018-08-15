package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceStatistics;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SequenceImpl implements Sequence {

    private SequenceStatistics sequenceStatistics;

    //TODO:: think about branching
    private List<AsyncStep> steps = new ArrayList<>();

//    private StepImpl head = null;

    @Override
    public Sequence step(Step step) {
        this.steps.add((AsyncStep) step);
//        if (head == null)
//            head = (StepImpl) step;
        return this;
    }

    @Override
    public List<AsyncStep> getSteps() {
        return steps;
    }

    @Override
    public SequenceStatistics statistics() {
        return sequenceStatistics;
    }

//    public StepImpl rootStep() {
//        return head;
//    }

    public CompletableFuture<SequenceContext> buildSequenceFuture(Worker worker, SequenceContext context) {
        Iterator<AsyncStep> iterator = steps.iterator();
        if (!iterator.hasNext()) {
            return CompletableFuture.completedFuture(context);
        }
        CompletableFuture<SequenceContext> chainedFuture = iterator.next().asyncExec(context);
        while (iterator.hasNext()) {
            AsyncStep step = iterator.next();
            chainedFuture = chainedFuture.thenCompose(step::asyncExec);
        }
        return chainedFuture;
    }
}
