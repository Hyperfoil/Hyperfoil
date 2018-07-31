package io.sailrocket.api;

public interface StatefulSequence {
    StatefulSequence request(Step step);
}
