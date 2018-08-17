package io.sailrocket.core.impl;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.BodyExtractor;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.spi.Validators;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StepImpl implements AsyncStep {
    private static final Logger log = LoggerFactory.getLogger(StepImpl.class);
    private static final boolean trace = log.isTraceEnabled();

    private String endpoint;
    private ByteBuf payload;
    private HttpMethod httpMethod = HttpMethod.GET;


    private Map<String, String> params = new HashMap<>();
    private Validators validators;
    private List<BodyExtractor> extractors = new ArrayList<>();

    @Override
    public Step path(String path) {
        this.endpoint = path;
        return this;
    }

    @Override
    public Step payload(ByteBuf payload) {
        this.payload = payload;
        return this;
    }

    @Override
    public Step param(String name, String value) {
        this.params.put(name, value);
        return this;
    }

    @Override
    public Step validators(Validators validators) {
        this.validators = validators;
        return this;
    }

    @Override
    public Step httpMethod(HttpMethod method) {
        this.httpMethod = method;
        return this;
    }

    @Override
    public CompletableFuture<SequenceContext> asyncExec(SequenceContext sequenceContext) {
        if (trace) {
            log.trace("Async step begin: request to {}", endpoint);
        }

        HttpRequest request;
        if (payload == null) {
            request = sequenceContext.clientPool().request(HttpMethod.GET, endpoint, null);
        } else {
            request = sequenceContext.clientPool().request(HttpMethod.POST, endpoint, payload.duplicate());
        }

        CompletableFuture<SequenceContext> completion = new CompletableFuture<>();

        if (payload != null) {
            request.putHeader("content-length", "" + payload.readableBytes());
        }
        request.statusHandler(code -> {
            sequenceContext.sequenceStats().addStatus(code);
            if (validators != null && validators.hasStatusValidator())
               sequenceContext.validatorResults().addStatus(validators.statusValidator().validate(null, code));
        }).bodyPartHandler(body -> {
            if (validators != null && validators.hasBodyValidator())
                validators.bodyValidator().validateData(null, body);
            //TODO:: populate session values here
            // TODO: this does not work, extractor moves readerIndex()
            this.extractors.forEach(bodyExtractor -> bodyExtractor.extractData(body, null));
        }).resetHandler(frame -> {
            // TODO: what is reset handler? Not used ATM
            sequenceContext.sequenceStats().resetCount++;
        }).endHandler(() -> {
            if (trace) {
                log.trace("Request completed.");
            }
            if (validators != null && validators.hasBodyValidator())
                sequenceContext.validatorResults().addBody(validators.bodyValidator().validate(null));
            sequenceContext.sequenceStats().responseCount++;
            completion.complete(sequenceContext);
        }).exceptionHandler(throwable -> {
            if (trace) {
                log.trace("Request to {} failed", throwable, endpoint);
            }
            completion.completeExceptionally(throwable);
        });

        if (trace) {
            log.trace("Starting a request to {}", endpoint);
        }
        sequenceContext.sequenceStats().requestCount++;
        request.end();

        return completion;
    }

    public void params(Map<String, String> params) {
        this.params.putAll(params);
    }
}
