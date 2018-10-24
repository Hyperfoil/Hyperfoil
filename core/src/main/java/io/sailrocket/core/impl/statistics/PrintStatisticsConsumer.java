/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sailrocket.core.impl.statistics;

import io.sailrocket.api.config.Simulation;

public class PrintStatisticsConsumer extends StatisticsCollector {
    public PrintStatisticsConsumer(Simulation simulation) {
        super(simulation);
    }

    public void print() {
        visitStatistics(((phase, sequence, snapshot) -> {
            System.out.format("%s/%s : total requests/responses %d, max %.2f, min %.2f, mean %.2f, 90th centile: %.2f%n",
                  phase.name(), sequence.name(),
                  snapshot.requestCount,
                  snapshot.histogram.getMaxValue() / 1_000_000.0,
                  snapshot.histogram.getMinValue() / 1_000_000.0,
                  snapshot.histogram.getMean() / 1_000_000.0,
                  snapshot.histogram.getValueAtPercentile(99.0) / 1_000_000.0
            );
        }));
    }
}
