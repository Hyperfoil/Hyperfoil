package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;

import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class Phase implements Serializable {
   protected static final Logger log = LoggerFactory.getLogger(Phase.class);
   protected static final boolean trace = log.isTraceEnabled();

   @Visitor.Ignore
   protected final SerializableSupplier<Benchmark> benchmark;
   @Visitor.Ignore
   public final int id;
   public final int iteration;
   @Visitor.Ignore
   public final String name;
   public final Scenario scenario;
   public final long startTime;
   public final Collection<String> startAfter;
   public final Collection<String> startAfterStrict;
   public final Collection<String> terminateAfterStrict;
   public final long duration;
   public final long maxDuration;
   // identifier for sharing resources across iterations
   public final String sharedResources;

   public Phase(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                Collection<String> startAfter, Collection<String> startAfterStrict,
                Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources) {
      this.benchmark = benchmark;
      this.id = id;
      this.iteration = iteration;
      this.name = name;
      this.terminateAfterStrict = terminateAfterStrict;
      this.maxDuration = maxDuration;
      this.startAfter = startAfter;
      this.startAfterStrict = startAfterStrict;
      this.scenario = scenario;
      this.startTime = startTime;
      this.duration = duration;
      this.sharedResources = sharedResources;
      if (scenario == null) {
         throw new BenchmarkDefinitionException("Scenario was not set for phase '" + name + "'");
      }

   }

   public int id() {
      return id;
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
    * @return Phases that must be finished (not starting any further user sessions) in order to start.
    */
   public Collection<String> startAfter() {
      return startAfter;
   }

   /**
    * @return Phases that must be terminated (not running any user sessions) in order to start.
    */
   public Collection<String> startAfterStrict() {
      return startAfterStrict;
   }

   /**
    * @return Phases that must be terminated in order to terminate this phase.
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

   public abstract String description();

   public static class AtOnce extends Phase {
      public final int users;

      public AtOnce(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, int users) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, 0, maxDuration, sharedResources);
         if (duration > 0) {
            log.warn("Duration for phase {} is ignored.", duration);
         }
         this.users = users;
      }

      @Override
      public String description() {
         return users + " users at once";
      }
   }

   public static class Always extends Phase {
      public final int users;

      public Always(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                    Collection<String> startAfter, Collection<String> startAfterStrict,
                    Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, int users) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         if (duration < 0) {
            throw new BenchmarkDefinitionException("Duration was not set for phase '" + name + "'");
         }
         this.users = users;
      }

      @Override
      public String description() {
         return users + " users always";
      }

   }

   public enum SessionLimitPolicy {
      /**
       * Cancel all sessions that did not start yet if the session limit is reached.
       */
      FAIL,
      /**
       * Continue even if we've reached maximum sessions.
       */
      CONTINUE
   }

   public abstract static class OpenModelPhase extends Phase {
      public final boolean variance;
      public final int maxSessions;
      public final SessionLimitPolicy sessionLimitPolicy;

      public OpenModelPhase(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime, Collection<String> startAfter, Collection<String> startAfterStrict, Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.variance = variance;
         this.maxSessions = maxSessions;
         this.sessionLimitPolicy = sessionLimitPolicy;
         if (duration < 0) {
            throw new BenchmarkDefinitionException("Duration was not set for phase '" + name + "'");
         }
      }
   }

   public static class RampRate extends OpenModelPhase {
      public final double initialUsersPerSec;
      public final double targetUsersPerSec;

      public RampRate(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                      Collection<String> startAfter, Collection<String> startAfterStrict,
                      Collection<String> terminateAfterStrict,
                      long duration, long maxDuration,
                      String sharedResources, double initialUsersPerSec, double targetUsersPerSec,
                      boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources, variance, maxSessions, sessionLimitPolicy);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      @Override
      public String description() {
         return String.format("%.2f - %.2f users per second", initialUsersPerSec, targetUsersPerSec);
      }
   }

   public static class ConstantRate extends OpenModelPhase {
      public final double usersPerSec;

      public ConstantRate(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                          Collection<String> startAfter, Collection<String> startAfterStrict,
                          Collection<String> terminateAfterStrict,
                          long duration, long maxDuration, String sharedResources, double usersPerSec, boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources, variance, maxSessions, sessionLimitPolicy);
         this.usersPerSec = usersPerSec;
      }

      @Override
      public String description() {
         return String.format("%.2f users per second", usersPerSec);
      }
   }

   public static class Sequentially extends Phase {
      public final int repeats;

      public Sequentially(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                          Collection<String> startAfter, Collection<String> startAfterStrict,
                          Collection<String> terminateAfterStrict,
                          long duration, long maxDuration, String sharedResources, int repeats) {
         super(benchmark, id, iteration, name, scenario, startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, sharedResources);
         this.repeats = repeats;
      }

      @Override
      public String description() {
         return repeats + " times";
      }
   }

   public static class Noop extends Phase {
      public Noop(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Collection<String> startAfter, Collection<String> startAfterStrict, Collection<String> terminateAfterStrict, Scenario scenario) {
         super(benchmark, id, iteration, name, scenario, -1, startAfter, startAfterStrict, terminateAfterStrict, 0, -1, null);
      }

      @Override
      public String description() {
         return "";
      }
   }
}
