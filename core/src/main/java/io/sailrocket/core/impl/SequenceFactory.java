package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClient;
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

    public static CompletableFuture<SequenceState> buildSequanceFuture(SequenceImpl sequence) {
        //TODO:: there's got to be a better way of doing this, this is a nasty code smell
        CompletableFuture<SequenceState> sequenceFuture = null;

        for (int i = 0; i < sequence.getSteps().size(); i++) {
            if (i == 0) {
                sequenceFuture = sequence.getSteps().get(i).asyncExec(new ClientSessionImpl(sequence.getHttpClient()));
            } else {
                int finalI = i;
                sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(finalI).asyncExec(session));
            }
        }

        return sequenceFuture;
    }
}
