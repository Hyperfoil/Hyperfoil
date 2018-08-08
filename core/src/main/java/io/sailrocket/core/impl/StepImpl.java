package io.sailrocket.core.impl;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.DataExtractor;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.spi.Validators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StepImpl implements AsyncStep {

    private String endpoint;
    private ByteBuf payload;
    private HttpMethod httpMethod = HttpMethod.GET;


    private Map<String, String> params = new HashMap<>();
    private Validators validators;
    private List<DataExtractor<?>> extractors = new ArrayList<>();

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

        //Future to handle result of http invocation
        CompletableFuture<SequenceContext> resultFuture = CompletableFuture.supplyAsync(() -> {

            HttpRequest request = sequenceContext.clientPool().request(payload != null ? HttpMethod.POST : HttpMethod.GET, endpoint);

            if (payload != null) {
                request.putHeader("content-length", "" + payload.readableBytes());
            }
            request.statusHandler(code -> {
                int status = (code - 200) / 100;
                if (status >= 0 && status < sequenceContext.sequenceStats().statuses.length) {
                    sequenceContext.sequenceStats().statuses[status].increment();
                }
                if(validators.hasStatusValidator())
                   sequenceContext.validatorResults().addHeader(validators.statusValidator().validate(code));
            }).bodyHandler( body -> {
                if(validators.hasBodyValidator())
                    sequenceContext.validatorResults().addBody(validators.bodyValidator().validate(new String(body)));
            }).resetHandler(frame -> {
                sequenceContext.sequenceStats().resetCount.increment();
            }).endHandler(response -> {

                //TODO:: populate session values here
                this.extractors.forEach(dataExtractor -> dataExtractor.extractData(response));

                sequenceContext.sequenceStats().responseCount.increment();

            });
            if (payload != null) {
                request.end(payload.duplicate());
            } else {
                request.end();
            }

            //this is passed onto the next step
            return sequenceContext;
        });

        return resultFuture;
    }

}
