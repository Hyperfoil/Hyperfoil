package io.hyperfoil.core.metric;

import io.hyperfoil.function.SerializableBiFunction;

public interface MetricSelector extends SerializableBiFunction<String, String, String> {
}
