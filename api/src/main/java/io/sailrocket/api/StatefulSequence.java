package io.sailrocket.api;

public interface StatefulSequence {
    StatefulSequence request(Request request);
}
