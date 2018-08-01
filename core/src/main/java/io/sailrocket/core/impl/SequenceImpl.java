package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.HttpClient;

import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;

import java.util.ArrayList;
import java.util.List;

public class SequenceImpl implements Sequence {

    private HttpClient httpClient;

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

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
