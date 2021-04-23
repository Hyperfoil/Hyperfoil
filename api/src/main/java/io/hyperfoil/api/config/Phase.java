package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;

import io.hyperfoil.function.SerializableSupplier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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

   public Phase(SerializableSupplier<Benchmark> benchmark, int id, int iteration, String name, Scenario scenario, long startTime,
                Collection<String> startAfter, Collection<String> startAfterStrict,
                Collection<String> terminateAfterStrict, long duration, long maxDuration, String sharedResources, Model model) {
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
      this.model = model;
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
}
