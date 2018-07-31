package io.sailrocket.api;

import java.util.concurrent.CompletableFuture;

public interface Step {

    Step endpoint(String endpoint);

    Step check();

    //TODO:: collection of validators to allow configurable validation
    Validator<Header> headerValidator();
    Validator<String> bodyValidator();

    // Double Linked List of steps to create a sequence chain
    // TODO:: look at externalising
    CompletableFuture<Step> next();

    CompletableFuture<Step> prev();

}
