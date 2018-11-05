package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.Phase;
import io.sailrocket.core.util.Util;

/**
 * The builder creates a matrix of phases (not just single phase); we allow multiple iterations of a phase
 * (with increasing number of users) and multiple forks (different scenarios, but same configuration).
 */
public abstract class PhaseBuilder<PB extends PhaseBuilder> {
   protected final String name;
   protected final SimulationBuilder parent;
   protected long startTime = -1;
   protected Collection<PhaseReference> startAfter = new ArrayList<>();
   protected Collection<PhaseReference> startAfterStrict = new ArrayList<>();
   protected Collection<PhaseReference> terminateAfterStrict = new ArrayList<>();
   protected long duration = -1;
   protected long maxDuration = -1;
   protected int maxIterations = 1;
   protected List<PhaseForkBuilder> forks = new ArrayList<>();

   protected PhaseBuilder(SimulationBuilder parent, String name) {
      this.name = name;
      this.parent = parent;
      parent.addPhase(name, this);
   }

   public SimulationBuilder endPhase() {
      return parent;
   }

   public String name() {
      return name;
   }

   public ScenarioBuilder scenario() {
      if (forks.isEmpty()) {
         PhaseForkBuilder fork = new PhaseForkBuilder(this, null);
         forks.add(fork);
         return fork.scenario;
      } else if (forks.size() == 1 && forks.get(0).name == null){
         throw new BenchmarkDefinitionException("Scenario for " + name + " already set!");
      } else {
         throw new BenchmarkDefinitionException("Scenario is forked; you need to specify another fork.");
      }
   }

   @SuppressWarnings("unchecked")
   private PB self() {
      return (PB) this;
   }

   public PhaseForkBuilder fork(String name) {
      if (forks.size() == 1 && forks.get(0).name == null) {
         throw new BenchmarkDefinitionException("Scenario for " + name + " already set!");
      } else {
         PhaseForkBuilder fork = new PhaseForkBuilder(this, name);
         forks.add(fork);
         return fork;
      }
   }

   public PB startTime(long startTime) {
      this.startTime = startTime;
      return self();

   }

   public PB startTime(String startTime) {
      return startTime(Util.parseToMillis(startTime));
   }

   public PB startAfter(String phase) {
      this.startAfter.add(new PhaseReference(phase, RelativeIteration.NONE, null));
      return self();
   }

   public PB startAfter(PhaseReference phase) {
      this.startAfter.add(phase);
      return self();
   }

   public PB startAfterStrict(String phase) {
      this.startAfterStrict.add(new PhaseReference(phase, RelativeIteration.NONE, null));
      return self();
   }

   public PB startAfterStrict(PhaseReference phase) {
      this.startAfterStrict.add(phase);
      return self();
   }

   public PB duration(long duration) {
      this.duration = duration;
      return self();
   }

   public PB duration(String duration) {
      return duration(Util.parseToMillis(duration));
   }

   public PB maxDuration(long maxDuration) {
      this.maxDuration = maxDuration;
      return self();
   }

   public PB maxDuration(String duration) {
      return maxDuration(Util.parseToMillis(duration));
   }

   public PB maxIterations(int iterations) {
      this.maxIterations = iterations;
      return self();
   }

   public Collection<Phase> build() {
      // normalize fork weights first
      if (forks.isEmpty()) {
         throw new BenchmarkDefinitionException("Scenario for " + name + " is not defined.");
      } else if (forks.size() == 1 && forks.get(0).name != null) {
         throw new BenchmarkDefinitionException(name + " has single fork: define scenario directly.");
      }
      double sumWeight = forks.stream().mapToDouble(f -> f.weight).sum();
      forks.forEach(f -> f.weight /= sumWeight);

      // create matrix of iteration|fork phases
      List<Phase> phases = IntStream.range(0, maxIterations)
            .mapToObj(iteration -> forks.stream().map(f -> buildPhase(iteration, f)))
            .flatMap(Function.identity()).collect(Collectors.toList());
      if (maxIterations > 1) {
         if (forks.size() > 1) {
            // add phase covering forks in each iteration
            IntStream.range(0, maxIterations).mapToObj(iteration -> {
               String iterationName = formatIteration(name, iteration);
               List<String> forks = this.forks.stream().map(f -> iterationName + "/" + f.name).collect(Collectors.toList());
               return new Phase.Noop(iterationName, forks, Collections.emptyList(), forks);
            }).forEach(phases::add);
         }
         // Referencing phase with iterations with RelativeIteration.NONE means that it starts after all its iterations
         List<String> lastIteration = Collections.singletonList(formatIteration(name, maxIterations - 1));
         phases.add(new Phase.Noop(name, lastIteration, Collections.emptyList(), lastIteration));
      } else if (forks.size() > 1) {
         // add phase covering forks
         List<String> forks = this.forks.stream().map(f -> name + "/" + f.name).collect(Collectors.toList());
         phases.add(new Phase.Noop(name, forks, Collections.emptyList(), forks));
      }
      return phases;
   }


   protected abstract Phase buildPhase(int iteration, PhaseForkBuilder f);

   int sliceValue(int value, double ratio) {
      double sliced = value * ratio;
      long rounded = Math.round(sliced);
      if (Math.abs(rounded - sliced) > 0.0001) {
         throw new BenchmarkDefinitionException("Cannot slice phase " + name + " cleanly: " + value + " * " + ratio + " is not an integer.");
      }
      return (int) rounded;
   }

   int numAgents() {
      return Math.max(parent.endSimulation().numAgents(), 1);
   }

   String iterationName(int iteration, String forkName) {
      if (maxIterations == 1) {
         assert iteration == 0;
         if (forkName == null) {
            return name;
         } else {
            return name + "/" + forkName;
         }
      } else {
         String iterationName = formatIteration(name, iteration);
         if (forkName == null) {
            return iterationName;
         } else {
            return iterationName + "/" + forkName;
         }
      }
   }

   private String formatIteration(String name, int iteration) {
      return String.format("%s/%03d", name, iteration);
   }

   long iterationStartTime(int iteration) {
      return iteration == 0 ? startTime : 0;
   }

   // Identifier for phase + fork, omitting iteration
   String sharedResources(PhaseForkBuilder fork) {
      if (fork == null || fork.name == null) {
         return name;
      } else {
         return name + "/" + fork;
      }
   }

   Collection<String> iterationReferences(Collection<PhaseReference> refs, int iteration, boolean addSelfPrevious) {
      Collection<String> names = new ArrayList<>();
      for (PhaseReference ref : refs) {
         switch (ref.iteration) {
            case NONE:
               names.add(ref.phase);
               break;
            case PREVIOUS:
               if (maxIterations <= 1) {
                  throw new BenchmarkDefinitionException(name + " referencing previous iteration of " + ref.phase + " but this phase has no iterations.");
               }
               if (iteration > 0) {
                  names.add(formatIteration(ref.phase, iteration - 1));
               }
               break;
            case SAME:
               if (maxIterations <= 1) {
                  throw new BenchmarkDefinitionException(name + " referencing previous iteration of " + ref.phase + " but this phase has no iterations.");
               }
               names.add(formatIteration(ref.phase, iteration));
               break;
            default:
               throw new IllegalArgumentException();
         }
      }
      if (addSelfPrevious && iteration > 0) {
         names.add(formatIteration(name, iteration - 1));
      }
      return names;
   }

   public static class AtOnce extends PhaseBuilder<AtOnce> {
      private int users;
      private int usersIncrement;

      AtOnce(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      public AtOnce users(int users) {
         this.users = users;
         return this;
      }

      public AtOnce users(int base, int increment) {
         this.users = base;
         this.usersIncrement = increment;
         return this;
      }

      @Override
      public Phase.AtOnce buildPhase(int i, PhaseForkBuilder f) {
         return new Phase.AtOnce(iterationName(i, f.name), f.scenario.build(), iterationStartTime(i),
               iterationReferences(startAfter, i, false), iterationReferences(startAfterStrict, i, true),
               iterationReferences(terminateAfterStrict, i, false), duration, maxDuration, sharedResources(f),
               sliceValue(users + usersIncrement * i, f.weight / numAgents()));
      }
   }

   public static class Always extends PhaseBuilder<Always> {
      private int users;
      private int usersIncrement;

      Always(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      @Override
      public Phase.Always buildPhase(int i, PhaseForkBuilder f) {
         return new Phase.Always(iterationName(i, f.name), f.scenario.build(), iterationStartTime(i),
               iterationReferences(startAfter, i, false), iterationReferences(startAfterStrict, i, true),
               iterationReferences(terminateAfterStrict, i, false), duration, maxDuration, sharedResources(f),
               sliceValue(users + usersIncrement * i, f.weight / numAgents()));
      }

      public Always users(int users) {
         this.users = users;
         return this;
      }

      public Always users(int base, int increment) {
         this.users = base;
         this.usersIncrement = increment;
         return this;
      }
   }

   public static class RampPerSec extends PhaseBuilder<RampPerSec> {
      private double initialUsersPerSec;
      private double initialUsersPerSecIncrement;
      private double targetUsersPerSec;
      private double targetUsersPerSecIncrement;
      private int maxSessionsEstimate;

      RampPerSec(SimulationBuilder parent, String name, double initialUsersPerSec, double targetUsersPerSec) {
         super(parent, name);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      public RampPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase.RampPerSec buildPhase(int i, PhaseForkBuilder f) {
         int maxSessionsEstimate;
         if (this.maxSessionsEstimate > 0) {
             maxSessionsEstimate = sliceValue(this.maxSessionsEstimate, f.weight / numAgents());
         } else {
            double maxInitialUsers = initialUsersPerSec + initialUsersPerSecIncrement * (maxIterations - 1);
            double maxTargetUsers = targetUsersPerSec + targetUsersPerSecIncrement * (maxIterations - 1);
            maxSessionsEstimate = (int) Math.ceil(Math.max(maxInitialUsers, maxTargetUsers) * f.weight / numAgents());
         }
         return new Phase.RampPerSec(iterationName(i, f.name), f.scenario.build(),
               iterationStartTime(i), iterationReferences(startAfter, i, false),
               iterationReferences(startAfterStrict, i, true), iterationReferences(terminateAfterStrict, i, false),
               duration, maxDuration,
               (initialUsersPerSec + initialUsersPerSecIncrement * i) * f.weight / numAgents(),
               (targetUsersPerSec + targetUsersPerSecIncrement * i) * f.weight / numAgents(),
               sharedResources(f), maxSessionsEstimate);
      }

      public RampPerSec initialUsersPerSec(double initialUsersPerSec) {
         this.initialUsersPerSec = initialUsersPerSec;
         this.initialUsersPerSecIncrement = 0;
         return this;
      }

      public RampPerSec initialUsersPerSec(double base, double increment) {
         this.initialUsersPerSec = base;
         this.initialUsersPerSecIncrement = increment;
         return this;
      }

      public RampPerSec targetUsersPerSec(double targetUsersPerSec) {
         this.targetUsersPerSec = targetUsersPerSec;
         this.targetUsersPerSecIncrement = 0;
         return this;
      }

      public RampPerSec targetUsersPerSec(double base, double increment) {
         this.targetUsersPerSec = base;
         this.targetUsersPerSecIncrement = increment;
         return this;
      }
   }

   public static class ConstantPerSec extends PhaseBuilder<ConstantPerSec> {
      private double usersPerSec;
      private double usersPerSecIncrement;
      private int maxSessionsEstimate;

      ConstantPerSec(SimulationBuilder parent, String name, double usersPerSec) {
         super(parent, name);
         this.usersPerSec = usersPerSec;
      }

      public ConstantPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase.ConstantPerSec buildPhase(int i, PhaseForkBuilder f) {
         int maxSessionsEstimate;
         if (this.maxSessionsEstimate <= 0) {
            maxSessionsEstimate = (int) Math.ceil(f.weight / numAgents() * (usersPerSec + usersPerSecIncrement * (maxIterations - 1)));
         } else {
            maxSessionsEstimate = sliceValue(this.maxSessionsEstimate, f.weight / numAgents());
         }
         return new Phase.ConstantPerSec(iterationName(i, f.name), f.scenario.build(), iterationStartTime(i),
               iterationReferences(startAfter, i, false), iterationReferences(startAfterStrict, i, true),
               iterationReferences(terminateAfterStrict, i, false), duration, maxDuration,
               sharedResources(f), (usersPerSec + usersPerSecIncrement * i) * f.weight / numAgents(), maxSessionsEstimate);
      }

      public ConstantPerSec usersPerSec(double usersPerSec) {
         this.usersPerSec = usersPerSec;
         return this;
      }

      public ConstantPerSec usersPerSec(double base, double increment) {
         this.usersPerSec = base;
         this.usersPerSecIncrement = increment;
         return this;
      }
   }

   public static class Discriminator {
      private final SimulationBuilder parent;
      private final String name;

      Discriminator(SimulationBuilder parent, String name) {
         this.parent = parent;
         this.name = name;
      }

      public AtOnce atOnce(int users) {
         return new PhaseBuilder.AtOnce(parent, name, users);
      }

      public Always always(int users) {
         return new PhaseBuilder.Always(parent, name, users);
      }

      public RampPerSec rampPerSec(int initialUsersPerSec, int targetUsersPerSec) {
         return new RampPerSec(parent, name, initialUsersPerSec, targetUsersPerSec);
      }

      public ConstantPerSec constantPerSec(int usersPerSec) {
         return new ConstantPerSec(parent, name, usersPerSec);
      }
   }
}
