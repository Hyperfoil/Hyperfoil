package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClient;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;

public class ClientSessionImpl implements SequenceContext {

    private HttpClient httpClient;
    private Worker worker;

    public ClientSessionImpl(HttpClient httpClient, Worker worker) {
        this.httpClient = httpClient;
        this.worker = worker;
    }

    @Override
    public HttpClient client() {
        return httpClient;
    }

    @Override
    public Worker worker() {
        return worker;
    }
}
