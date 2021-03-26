package io.hyperfoil.core.parser;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.PhaseReference;
import io.hyperfoil.api.config.RelativeIteration;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.core.util.Util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * This provides a convenient way to define alternating ramp-ups and steady-states.
 */
class StaircaseParser extends AbstractParser<BenchmarkBuilder, StaircaseParser.StaircaseBuilder> {
   private static final Logger log = LogManager.getLogger(StaircaseParser.class);

   public StaircaseParser() {
      register("initialRampUpDuration", new PropertyParser.String<>(StaircaseBuilder::initialRampUpDuration));
      register("steadyStateDuration", new PropertyParser.String<>(StaircaseBuilder::steadyStateDuration));
      register("rampUpDuration", new PropertyParser.String<>(StaircaseBuilder::rampUpDuration));
      register("initialUsersPerSec", new PropertyParser.Double<>(StaircaseBuilder::initialUsersPerSec));
      register("incrementUsersPerSec", new PropertyParser.Double<>(StaircaseBuilder::incrementUsersPerSec));
      register("maxIterations", new PropertyParser.Int<>(StaircaseBuilder::maxIterations));
      register("maxSessions", new PropertyParser.Int<>(StaircaseBuilder::maxSessions));
      register("scenario", new Adapter<>(StaircaseBuilder::scenario, new ScenarioParser()));
      register("forks", new Adapter<>(StaircaseBuilder::phase, new PhaseForkParser()));
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      StaircaseBuilder builder = new StaircaseBuilder(target);
      callSubBuilders(ctx, builder);
      builder.apply();
   }

   static class StaircaseBuilder {
      private final BenchmarkBuilder benchmark;
      private final PhaseBuilder.ConstantRate steadyState;
      private long initialRampUpDuration;
      private long steadyStateDuration;
      private long rampUpDuration;
      private long maxOverrun;
      private double initialUsersPerSec;
      private double incrementUsersPerSec;
      private int maxIterations;
      private int maxSessions;

      public StaircaseBuilder(BenchmarkBuilder benchmark) {
         this.benchmark = benchmark;
         steadyState = benchmark.addPhase("steadyState").constantRate(0);
      }

      public void initialRampUpDuration(String duration) {
         this.initialRampUpDuration = Util.parseToMillis(duration);
      }

      public void steadyStateDuration(String duration) {
         this.steadyStateDuration = Util.parseToMillis(duration);
      }

      public void rampUpDuration(String duration) {
         this.rampUpDuration = Util.parseToMillis(duration);
      }

      public void maxOverrun(String duration) {
         this.maxOverrun = Util.parseToMillis(duration);
      }

      public void initialUsersPerSec(double usersPerSec) {
         this.initialUsersPerSec = usersPerSec;
      }

      public void incrementUsersPerSec(double usersPerSec) {
         this.incrementUsersPerSec = usersPerSec;
      }

      public void maxIterations(int iterations) {
         this.maxIterations = iterations;
      }

      public void maxSessions(int sessions) {
         this.maxSessions = sessions;
      }

      public ScenarioBuilder scenario() {
         return steadyState.scenario();
      }

      public PhaseBuilder<?> phase() {
         return steadyState;
      }

      public void apply() {
         if (steadyStateDuration <= 0) {
            throw new BenchmarkDefinitionException("Staircase must define 'steadyStateDuration'");
         }
         if (maxIterations <= 0) {
            throw new BenchmarkDefinitionException("Staircase must define 'maxIterations'");
         }
         if (incrementUsersPerSec <= 0) {
            throw new BenchmarkDefinitionException("Staircase must define 'incrementUsersPerSec'");
         }
         if (initialUsersPerSec <= 0) {
            initialUsersPerSec = incrementUsersPerSec;
         }
         steadyState
               .duration(steadyStateDuration)
               .usersPerSec(initialUsersPerSec, incrementUsersPerSec)
               .maxIterations(maxIterations);
         if (maxOverrun > 0) {
            steadyState.maxDuration(steadyStateDuration + maxOverrun);
         }
         if (maxSessions > 0) {
            steadyState.maxSessions(maxSessions);
         }
         if (initialRampUpDuration <= 0) {
            initialRampUpDuration = rampUpDuration;
         }
         if (initialRampUpDuration > 0) {
            PhaseBuilder.RampRate initialRampUp = benchmark.addPhase("initialRampUp").rampRate(0, 0)
                  .targetUsersPerSec(initialUsersPerSec).duration(initialRampUpDuration);
            if (maxOverrun > 0) {
               initialRampUp.maxDuration(initialRampUpDuration + maxOverrun);
            }
            if (maxSessions > 0) {
               initialRampUp.maxSessions(maxSessions);
            }
            steadyState.startAfter(initialRampUp.name());
            initialRampUp.readForksFrom(steadyState);
         }
         if (rampUpDuration > 0) {
            if (maxIterations > 1) {
               PhaseBuilder.RampRate rampUp = benchmark.addPhase("rampUp").rampRate(0, 0)
                     .duration(rampUpDuration)
                     .initialUsersPerSec(initialUsersPerSec, incrementUsersPerSec)
                     .targetUsersPerSec(initialUsersPerSec + incrementUsersPerSec, incrementUsersPerSec)
                     .maxIterations(maxIterations - 1).forceIterations(true)
                     .startAfter(new PhaseReference(steadyState.name(), RelativeIteration.SAME, null));
               if (maxOverrun > 0) {
                  rampUp.maxDuration(rampUpDuration + maxOverrun);
               }
               if (maxSessions > 0) {
                  rampUp.maxSessions(maxSessions);
               }
               if (maxIterations > 1) {
                  steadyState.startAfter(new PhaseReference(rampUp.name(), RelativeIteration.PREVIOUS, null));
               }
               rampUp.readForksFrom(steadyState);
            }
         } else {
            log.warn("No 'rampUpDuration' defined. There won't be continuous load.");
         }
      }
   }
}
