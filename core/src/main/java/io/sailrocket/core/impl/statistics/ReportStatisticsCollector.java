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

import io.sailrocket.api.Phase;
import io.sailrocket.api.Report;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.Simulation;
import io.sailrocket.api.StatisticsSnapshot;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public class ReportStatisticsCollector extends StatisticsCollector {
    private final StatisticsConsumer addReport = this::addReport;
    private Map<String, Report> reportMap;

    public ReportStatisticsCollector(Simulation simulation) {
        super(simulation, true);
    }

    public Map<String, Report> reports() {
        reportMap = new HashMap<>();
        visitStatistics(addReport);
        return reportMap;
    }

    private boolean addReport(Phase phase, Sequence sequence, StatisticsSnapshot snapshot) {
        Report report = new Report(simulation.tags());
        report.measures(
              snapshot.requestCount,
              0,
              snapshot.histogram,
              snapshot.responseCount,
              0,
              snapshot.connectFailureCount,
              snapshot.resetCount,
              snapshot.requestCount,
              snapshot.statuses(),
              0, //clientPool.bytesRead(),  //TODO::get bytes from client pool
              0  //clientPool.bytesWritten()
        );
        reportMap.put(phase.name() + "/" + sequence.name(), report);
        return false;
    }
}
