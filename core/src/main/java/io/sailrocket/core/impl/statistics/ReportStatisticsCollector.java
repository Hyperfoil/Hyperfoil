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

import io.sailrocket.api.Report;
import io.sailrocket.api.Statistics;
import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ReportStatisticsCollector implements Consumer<Statistics> {

    private Map<Statistics, Report> reportMap;
    JsonObject tags;
    private long rate;
    private long duration;
    private long startTime;

    public ReportStatisticsCollector(JsonObject tags, long rate, long duration, long startTime) {
        this.tags = tags;
        this.rate = rate;
        this.duration = duration;
        this.startTime = startTime;
        reportMap = new HashMap<>();
    }

    @Override
    public void accept(Statistics statistics) {

        Report statisticsReport = new Report(tags);

        long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(duration);
        long elapsed = System.nanoTime() - startTime;
        Histogram cp = statistics.histogram.copy();
        cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
        cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        statisticsReport.measures(
                expectedRequests,
                elapsed,
                cp,
                statistics.responseCount,
                ratio(statistics),
                statistics.connectFailureCount,
                statistics.resetCount,
                statistics.resetCount,
                statistics.statuses(),
                0, //clientPool.bytesRead(),  //TODO::get bytes from client pool
                0  //clientPool.bytesWritten()
        );

        reportMap.putIfAbsent(statistics, statisticsReport);
    }

    //TODO: move this calc
    private double ratio(Statistics sequenceStats) {
        long end = Math.min(System.nanoTime(), startTime + duration);
        long expected = rate * (end - startTime) / 1000000000;
        return sequenceStats.requestCount / (double) expected;
    }

    public Report getFirstReport(){
        return reportMap.values().stream().findFirst().get();
    }

    public Collection<Report> reports() {
        return reportMap.values();
    }
}
