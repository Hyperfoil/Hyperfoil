package io.sailrocket.api;

public interface DataExtractor<T> {

    void extractData(HttpRequest httpRequest, SequenceState sequenceState);
}
