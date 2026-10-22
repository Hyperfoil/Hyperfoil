package io.hyperfoil.core.impl;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;

public class SubMillisecondDetector {

   private static final double THRESHOLD = 1000.0;

   private SubMillisecondDetector() {
   }

   public static boolean requiresSubMillisecondPrecision(Benchmark benchmark, int agentId) {
      for (Phase phase : benchmark.phases()) {
         if (phase.model instanceof Model.ConstantRate model) {
            double rate = benchmark.slice(model.usersPerSec, agentId);
            if (rate > THRESHOLD) {
               return true;
            }
         } else if (phase.model instanceof Model.RampRate model) {
            double initialRate = benchmark.slice(model.initialUsersPerSec, agentId);
            double targetRate = benchmark.slice(model.targetUsersPerSec, agentId);
            if (initialRate > THRESHOLD || targetRate > THRESHOLD) {
               return true;
            }
         }
      }
      return false;
   }
}
