package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.function.SerializableSupplier;

public final class Phase implements Serializable {
   protected static final Logger log = LogManager.getLogger(Phase.class);
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
   public final Model model;
   public final boolean isWarmup;
   public final Map<String, SLA[]> customSlas;
   public final StartWithDelay startWithDelay;

   public Phase(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime, Collection<String> startAfter, Collection<String> startAfterStrict, Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, boolean isWarmup, Model model, Map<String, SLA[]> customSlas, StartWithDelay startWithDelay) {
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
      this.isWarmup = isWarmup;
      this.model = model;
      this.customSlas = customSlas;
      this.startWithDelay = startWithDelay;
      if (scenario == null) {
         throw new BenchmarkDefinitionException("Scenario was not set for phase '" + name + "'");
      }
      model.validate(this);
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

   public String description() {
      return model.description();
   }

   /**
    * @return Start with delay object defining the phase to which it is coupled, the current phase will start after a
    * fixed time (delay) from the start of the coupled phase.
    */
   public StartWithDelay startWithDelay() {
      return startWithDelay;
   }

   /**
    * Compute and return all phase's dependencies
    * @return list of phase names
    */
   public Collection<String> getDependencies() {
      Stream<String> dependencies = Stream.concat(startAfter().stream(), startAfterStrict().stream());
      if (startWithDelay() != null) {
         dependencies = Stream.concat(dependencies, Stream.of(startWithDelay().phase));
      }
      return dependencies.collect(Collectors.toList());
   }
}
