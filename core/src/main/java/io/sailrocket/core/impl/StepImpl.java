package io.sailrocket.core.impl;

import io.sailrocket.api.DataExtractor;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.SequenceState;
import io.sailrocket.api.Step;
import io.sailrocket.api.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StepImpl implements Step {

    private String endpoint;
    private List<Validator<?>> validators = new ArrayList<>();
    private List<DataExtractor<?>> extractors = new ArrayList<>();
    private Step next;

    @Override
    public Step endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    @Override
    public Step validator(Validator<?> validator) {
        this.validators.add(validator);
        return this;
    }

    @Override
    public Step next(Step next) {
        this.next = next;
        return this;
    }

    @Override
    public Step getNext() {
        return this.next;
    }

    @Override
    public CompletableFuture<SequenceState> asyncExec(SequenceState sequenceState) {
        return CompletableFuture.supplyAsync(() -> {

            // TODO:: bubble exceptions out of stack
//            if ( httpClient == null)
//                throw new NoHttpClientException();

            //TODO:: plug this into the async client
            HttpRequest response = sequenceState.getClient().request(HttpMethod.GET, this.endpoint); //todo:: HttpMethod must be configurable
            SequenceState returnSession = sequenceState;

            //TODO:: run validators
            //validators.forEach(validator -> validator.validate());

            //TODO:: populate session values here
            this.extractors.forEach(dataExtractor -> dataExtractor.extractData(response, sequenceState));

            return sequenceState;
        });
    }

    public String getEndpoint(){
        return this.endpoint;
    }
}
