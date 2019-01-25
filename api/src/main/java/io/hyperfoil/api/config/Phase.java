package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;

import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class Phase implements Serializable {
   protected static final Logger log = LoggerFactory.getLogger(Phase.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected final SerializableSupplier<Benchmark> benchmark;
   public final String name;
   public final Scenario scenario;
   public final long startTime;
   public final Collection<String> startAfter;
   public final Collection<String> startAfterStrict;
   public final Collection<String> terminateAfterStrict;
   public final long duration;
   public final long maxDuration;
   public final int maxUnfinishedSessions;
   // identifier for sharing resources across iterations
   public final String sharedResources;

   public Phase(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                Collection<String> startAfter, Collection<String> startAfterStrict,
                Collection<String> terminateAfterStrict, long duration, long maxDuration, int maxUnfinishedSessions, String sharedResources) {
      this.benchmark = benchmark;
      this.name = name;
      this.terminateAfterStrict = terminateAfterStrict;
      this.maxDuration = maxDuration;
      this.startAfter = startAfter;
      this.startAfterStrict = startAfterStrict;
      this.scenario = scenario;
      this.startTime = startTime;
      this.duration = duration;
      this.maxUnfinishedSessions = maxUnfinishedSessions;
      this.sharedResources = sharedResources;
      if (duration < 0) {
         throw new BenchmarkDefinitionException("Duration was not set for phase '" + name + "'");
      }
      if (scenario == null) {
         throw new BenchmarkDefinitionException("Scenario was not set for phase '" + name + "'");
      }

   }

   public String name() {
      return name;
   }

   public Scenario scenario() {
      return scenario;
   }

   /**
    * @return Start time in milliseconds after benchmark start, or negative value if the phase should start immediately
    * after its dependencies ({@link #startAfter()} and {@link #startAfterStrict()} are satisfied.
    */
   public long startTime() {
      return startTime;
   }

   /**
    * Phases that must be finished (not starting any further user sessions) in order to start.
    */
   public Collection<String> startAfter() {
      return startAfter;
   }

   /**
    * Phases that must be terminated (not running any user sessions) in order to start.
    */
   public Collection<String> startAfterStrict() {
      return startAfterStrict;
   }

   /**
    * Phases that must be terminated in order to terminate this phase.
    */
   public Collection<String> terminateAfterStrict() {
      return terminateAfterStrict;
   }

   /**
    * @return Duration in milliseconds over which new user sessions should be started.
    */
   public long duration() {
      return duration;
   }

   /**
    * @return Duration in milliseconds over which user sessions can run. After this time no more requests are allowed
    * and the phase should terminate.
    */
   public long maxDuration() {
      return maxDuration;
   }

   public Benchmark benchmark() {
      return benchmark.get();
   }

   private static int requirePositive(int value, String message) {
      if (value <= 0) {
         throw new BenchmarkDefinitionException(message);
      }
      return value;
   }

   private static double requireNonNegative(double value, String message) {
      if (value < 0) {
         throw new BenchmarkDefinitionException(message);
      }
      return value;
   }

   public static class AtOnce extends Phase {
      public final int users;

      public AtOnce(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, int maxUnfinishedSessions, String sharedResources, int users) {
         super(benchmark, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, 0, maxDuration, maxUnfinishedSessions, sharedResources);
         if (duration > 0) {
            log.warn("Duration for phase {} is ignored.", duration);
         }
         this.users = requirePositive(users, "Phase " + name + " requires positive number of users!");
      }
   }

   public static class Always extends Phase {
      public final int users;

      public Always(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, int maxUnfinishedSessions, String sharedResources, int users) {
         super(benchmark, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, maxUnfinishedSessions, sharedResources);
         this.users = requirePositive(users, "Phase " + name + " requires positive number of users!");;
      }
   }

   public static class RampPerSec extends Phase {
      public final double initialUsersPerSec;
      public final double targetUsersPerSec;
      public final int maxSessionsEstimate;
      public final boolean variance;

      public RampPerSec(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                        Collection<String> startAfter, Collection<String> startAfterStrict,
                        Collection<String> terminateAfterStrict,
                        long duration, long maxDuration,
                        int maxUnfinishedSessions, String sharedResources, double initialUsersPerSec, double targetUsersPerSec,
                        boolean variance, int maxSessionsEstimate) {
         super(benchmark, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, maxUnfinishedSessions, sharedResources);
         this.initialUsersPerSec = requireNonNegative(initialUsersPerSec, "Phase " + name + " requires non-negative number of initial users per second!");
         this.targetUsersPerSec = requireNonNegative(targetUsersPerSec, "Phase " + name + " requires non-negative number of target users per second!");
         this.variance = variance;
         this.maxSessionsEstimate = maxSessionsEstimate;
      }
   }

   public static class ConstantPerSec extends Phase {
      public final double usersPerSec;
      public final int maxSessionsEstimate;
      public final boolean variance;

      public ConstantPerSec(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                            Collection<String> startAfter, Collection<String> startAfterStrict,
                            Collection<String> terminateAfterStrict,
                            long duration, long maxDuration, int maxUnfinishedSessions, String sharedResources, double usersPerSec, boolean variance, int maxSessionsEstimate) {
         super(benchmark, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, maxUnfinishedSessions, sharedResources);
         this.usersPerSec = Phase.requireNonNegative(usersPerSec, "Phase " + name + " requires non-negative number of users per second!");
         this.variance = variance;
         this.maxSessionsEstimate = maxSessionsEstimate;
      }
   }

   public static class Sequentially extends Phase {
      public final int repeats;

      public Sequentially(SerializableSupplier<Benchmark> benchmark, String name, Scenario scenario, long startTime,
                          Collection<String> startAfter, Collection<String> startAfterStrict,
                          Collection<String> terminateAfterStrict,
                          long duration, long maxDuration, int maxUnfinishedSessions, String sharedResources, int repeats) {
         super(benchmark, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, maxUnfinishedSessions, sharedResources);
         this.repeats = Phase.requirePositive(repeats, "Phase " + name + " requires positive number of repeats!");
      }
   }

   public static class Noop extends Phase {
      public Noop(SerializableSupplier<Benchmark> benchmark, String name, Collection<String> startAfter, Collection<String> startAfterStrict, Collection<String> terminateAfterStrict, Scenario scenario) {
         super(benchmark, name, scenario, -1, startAfter, startAfterStrict, terminateAfterStrict, 0, -1, 0, null);
      }
   }
}
