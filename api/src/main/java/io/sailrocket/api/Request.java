package io.sailrocket.api;

public interface Request {

    Request endpoint(String endpoint);

    Request check();

}
