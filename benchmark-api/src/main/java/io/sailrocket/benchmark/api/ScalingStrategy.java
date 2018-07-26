package io.sailrocket.benchmark.api;

public interface ScalingStrategy {

    ScalingStrategy user(User user);

    ScalingStrategy then(ScalingStrategy scalingStrategy);
}
