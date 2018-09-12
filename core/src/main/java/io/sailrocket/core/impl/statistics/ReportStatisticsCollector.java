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
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class ReportStatisticsCollector extends StatisticsAggregator {
    private JsonObject tags;

    public ReportStatisticsCollector(Simulation simulation, JsonObject tags) {
        super(simulation);
        this.tags = tags;
    }

    public Map<String, Report> reports() {
        Map<String, Report> reportMap = new HashMap<>();
        visitStatistics((phase, sequence, snapshot) -> addReport(reportMap, phase, sequence, snapshot));
        return reportMap;
    }

    private void addReport(Map<String, Report> reportMap, Phase phase, Sequence sequence, StatisticsSnapshot snapshot) {
        Report report = new Report(tags);
        report.measures(
              0,
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
    }
}
