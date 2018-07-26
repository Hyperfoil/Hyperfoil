package io.sailrocket.benchmark.api;

public interface Request {

    Request endpoint(String endpoint);

    Request check();

}
