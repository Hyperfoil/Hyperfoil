package io.sailrocket.api;

public interface ScalingStrategy {

    ScalingStrategy user(User user);

    ScalingStrategy then(ScalingStrategy scalingStrategy);
}
