package io.sailrocket.core.impl;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.Step;
import io.sailrocket.api.HttpClient;

import java.util.ArrayList;
import java.util.List;

public class SequenceImpl implements Sequence {

    private HttpClient httpClient;

    //TODO:: think about branching
    private List<Step> steps = new ArrayList<>();

    @Override
    public Sequence step(Step step) {
        this.steps.add(step);
        return this;
    }

    @Override
    public List<Step> getSteps() {
        return steps;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
