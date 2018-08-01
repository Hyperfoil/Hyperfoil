package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceState;
import io.sailrocket.api.Step;

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

    public static CompletableFuture<SequenceState> buildSequenceFuture(SequenceImpl sequence) {

        CompletableFuture<SequenceState> rootFuture = new CompletableFuture().supplyAsync(() -> new ClientSessionImpl(sequence.getHttpClient()));
        return sequence.getSteps().stream()
                .reduce(rootFuture
                        , (sequenceFuture, step) -> sequenceFuture.thenCompose(sequenceState -> step.asyncExec(sequenceState))
                        , (sequenceFuture, e) -> sequenceFuture
                );
    }
}
