package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.Worker;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SequenceFactory {

    public static Sequence buildSequence(List<Step> steps) {

        SequenceImpl sequence = new SequenceImpl();

        steps.forEach(step ->
                sequence.step(step)
        );

        return sequence;
    }

    public static CompletableFuture<SequenceContext> buildSequenceFuture(SequenceImpl sequence, Worker worker) {

        CompletableFuture<SequenceContext> rootFuture = new CompletableFuture().supplyAsync(() ->
                new ClientSessionImpl(sequence.getHttpClientPool(), worker)
        );

        return sequence.getSteps().stream()
                .reduce(rootFuture
                        , (sequenceFuture, step) -> sequenceFuture.thenCompose(sequenceState -> step.asyncExec(sequenceState))
                        , (sequenceFuture, e) -> sequenceFuture
                );
    }
}
