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
package io.hyperfoil.core.impl.statistics;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.core.impl.Report;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public class ReportStatisticsCollector extends StatisticsCollector {
    private final StatisticsConsumer addReport = this::addReport;
    private Map<String, Report> reportMap;

    public ReportStatisticsCollector(Simulation simulation) {
        super(simulation);
    }

    public Map<String, Report> reports() {
        reportMap = new HashMap<>();
        visitStatistics(addReport, null);
        return reportMap;
    }

    private void addReport(Phase phase, String name, StatisticsSnapshot snapshot, CountDown countDown) {
        Report report = new Report(simulation.tags());
        report.measures(
              snapshot.requestCount,
              0,
              snapshot.histogram.copy(),
              snapshot.responseCount,
              0,
              snapshot.connectFailureCount,
              snapshot.resetCount,
              snapshot.requestCount,
              snapshot.statuses(),
              0, //clientPool.bytesRead(),  //TODO::get bytes from client pool
              0  //clientPool.bytesWritten()
        );
        reportMap.put(phase.name() + "/" + name, report);
    }
}
