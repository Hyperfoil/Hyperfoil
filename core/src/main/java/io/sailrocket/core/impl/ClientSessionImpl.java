package io.sailrocket.core.impl;

import io.sailrocket.api.HttpClient;
import io.sailrocket.api.SequenceState;

public class ClientSessionImpl implements SequenceState {

    private HttpClient httpClient;

    public ClientSessionImpl(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpClient getClient() {
        return httpClient;
    }
}
