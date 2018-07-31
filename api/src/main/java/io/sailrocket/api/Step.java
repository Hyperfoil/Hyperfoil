package io.sailrocket.api;

import java.util.concurrent.CompletableFuture;

public interface Step {

    Step endpoint(String endpoint);

    Step validator(Validator<?> validator);

    Step next(Step next);



    //TODO:: consider if methods below be in the "public" api, or whether we need a separate consumer api in core
    Step getNext();

    CompletableFuture<SequenceState> asyncExec(SequenceState sequenceState);
}
