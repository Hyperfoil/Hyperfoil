package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.HttpClientPool;

import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;

import java.util.ArrayList;
import java.util.List;

public class SequenceImpl implements Sequence {

    private HttpClientPool httpClientPool;

    //TODO:: think about branching
    private List<AsyncStep> steps = new ArrayList<>();

    @Override
    public Sequence step(Step step) {
        this.steps.add((AsyncStep) step);
        return this;
    }

    @Override
    public List<AsyncStep> getSteps() {
        return steps;
    }

    public HttpClientPool getHttpClientPool() {
        return httpClientPool;
    }

    public void setHttpClientPool(HttpClientPool httpClientPool) {
        this.httpClientPool = httpClientPool;
    }
}
