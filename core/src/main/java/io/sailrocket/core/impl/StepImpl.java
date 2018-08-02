package io.sailrocket.core.impl;

import io.sailrocket.api.DataExtractor;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.api.Step;
import io.sailrocket.api.Validator;
import io.sailrocket.core.client.RequestContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StepImpl implements AsyncStep {

    private String endpoint;
    private HttpMethod httpMethod = HttpMethod.GET; //todo:: HttpMethod must be configurable

    private Map<String, String> params = new HashMap<>();
    private List<Validator<?>> validators = new ArrayList<>();
    private List<DataExtractor<?>> extractors = new ArrayList<>();
    private Step next;

    @Override
    public Step path(String path) {
        this.endpoint = path;
        return this;
    }

    @Override
    public Step param(String name, String value) {
        this.params.put(name, value);
        return this;
    }

    @Override
    public Step validator(Validator<?> validator) {
        this.validators.add(validator);
        return this;
    }

    @Override
    public Step httpMethod(HttpMethod method) {
        this.httpMethod = httpMethod;
        return this;
    }

    @Override
    public CompletableFuture<SequenceContext> asyncExec(SequenceContext sequenceState) {

        RequestContext requestContext = new RequestContext(sequenceState, this.endpoint);

        //Future to handle result of http invocation
        CompletableFuture<SequenceContext> resultFuture = CompletableFuture.supplyAsync(() -> {

            // TODO:: bubble exceptions out of stack
//            if ( httpClient == null)
//                throw new NoHttpClientException();

            //TODO:: plug this into the async clientPool
            HttpRequest asyncRequest = sequenceState.clientPool().request(httpMethod, this.endpoint);

            SequenceContext returnSession = sequenceState;

            //TODO:: run validators
            //validators.forEach(validator -> validator.validate());

            //TODO:: populate session values here
            this.extractors.forEach(dataExtractor -> dataExtractor.extractData(asyncRequest));

            //this is passed onto the next step
            return sequenceState;
        });

        CompletableFuture<SequenceContext> workerFuture = sequenceState.worker().runSlot(10, requestContext).thenCompose(v -> resultFuture);

        return workerFuture;
    }

    public String getEndpoint(){
        return this.endpoint;
    }
}
