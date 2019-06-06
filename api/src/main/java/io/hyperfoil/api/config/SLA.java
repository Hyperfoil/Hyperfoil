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
package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class SLA implements Serializable {
   private final long window;
   private final double errorRate;
   private final long meanResponseTime;
   private final Collection<PercentileLimit> limits;

   public SLA(long window, double errorRate, long meanResponseTime, Collection<PercentileLimit> limits) {
      this.window = window;
      this.meanResponseTime = meanResponseTime;
      this.errorRate = errorRate;
      this.limits = limits;
   }

   public long window() {
      return window;
   }

   public double errorRate() {
      return errorRate;
   }

   public long meanResponseTime() {
      return meanResponseTime;
   }

   public Collection<PercentileLimit> percentileLimits() {
      return Collections.unmodifiableCollection(limits);
   }

   public SLA.Failure validate(String phase, String metric, StatisticsSnapshot statistics) {
      double actualErrorRate = (double) statistics.errors() / statistics.requestCount;
      if (actualErrorRate >= errorRate) {
         return new SLA.Failure(this, phase, metric, statistics.clone(),
               String.format("Error rate exceeded: required %.3f, actual %.3f", errorRate, actualErrorRate));
      }
      if (meanResponseTime < Long.MAX_VALUE) {
         double mean = statistics.histogram.getMean();
         if (mean >= meanResponseTime) {
            return new SLA.Failure(this, phase, metric, statistics.clone(),
                  String.format("Mean response time exceeded: required %d, actual %f", meanResponseTime, mean));
         }
      }
      for (SLA.PercentileLimit limit : limits) {
         long value = statistics.histogram.getValueAtPercentile(limit.percentile());
         if (value >= limit.responseTime()) {
            return new SLA.Failure(this, phase, metric, statistics.clone(),
                  String.format("Response time at percentile %f exceeded: required %d, actual %d", limit.percentile, limit.responseTime, value));
         }
      }
      return null;
   }

   public static class PercentileLimit implements Serializable {
      private final double percentile;
      private final long responseTime;

      public PercentileLimit(double percentile, long responseTime) {
         this.percentile = percentile;
         this.responseTime = responseTime;
      }

      public double percentile() {
         return percentile;
      }

      /**
       * @return Maximum allowed response time for given percentile, in nanoseconds.
       */
      public long responseTime() {
         return  responseTime;
      }
   }

   public static class Failure {
      private final SLA sla;
      private final String phase;
      private final String metric;
      private final StatisticsSnapshot statistics;
      private final String message;

      public Failure(SLA sla, String phase, String metric, StatisticsSnapshot statistics, String message) {
         this.sla = sla;
         this.phase = phase;
         this.metric = metric;
         this.statistics = statistics;
         this.message = message;
      }

      public SLA sla() {
         return sla;
      }

      public String phase() {
         return phase;
      }

      public String metric() {
         return metric;
      }

      public StatisticsSnapshot statistics() {
         return statistics;
      }

      public String message() {
         return message;
      }
   }

   public interface Provider extends Step {
      /**
       * @return Step ID that will allow us to match against the SLA.
       */
      int id();
      SLA[] sla();
      Sequence sequence();
   }
}
