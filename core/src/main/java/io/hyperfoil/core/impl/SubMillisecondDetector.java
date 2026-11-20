package io.hyperfoil.core.impl;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;

final class SubMillisecondDetector {

   private static final double THRESHOLD = 1000.0;

   private SubMillisecondDetector() {
   }

   /**
    * Rate is evaluated per phase (each fork is its own phase, scaled by its weight) and per agent
    * via {@link Benchmark#slice}, not on the benchmark's aggregate total.
    */
   static boolean requiresSubMillisecondPrecision(Benchmark benchmark, int agentId) {
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
