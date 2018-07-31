package io.sailrocket.core.api;

import io.sailrocket.api.Step;
import io.vertx.core.AsyncResult;

public interface AsyncStep extends Step {


    AsyncResult<?> invoke();

}
