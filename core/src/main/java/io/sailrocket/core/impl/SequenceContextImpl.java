package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.Sequence;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.client.SequenceStats;

public class SequenceContextImpl implements SequenceContext {

    private HttpClientPool httpClientPool;
    private Worker worker;
    private Sequence sequence;
    private SequenceStats sequenceStats;

    public SequenceContextImpl(Sequence sequence, Worker worker) {
        this.worker = worker;
        this.httpClientPool = worker.clientPool();
        sequence = sequence;
        sequenceStats = new SequenceStats();
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
    public Sequence sequence() {
        return sequence;
    }

    @Override
    public SequenceStats sequenceStats() {
        return sequenceStats;
    }
}
