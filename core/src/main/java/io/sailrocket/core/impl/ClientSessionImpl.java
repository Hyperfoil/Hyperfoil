package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.client.SequenceStats;

public class ClientSessionImpl implements SequenceContext {

    private HttpClientPool httpClientPool;
    private Worker worker;
    private SequenceStats sequenceStats;

    public ClientSessionImpl(HttpClientPool httpClientPool, Worker worker) {
        this.httpClientPool = httpClientPool;
        this.worker = worker;
    }

    @Override
    public HttpClientPool clientPool() {
        return httpClientPool;
    }

    @Override
    public Worker worker() {
        return worker;
    }

    @Override
    public SequenceStats sequenceStats() {
        return sequenceStats;
    }
}
