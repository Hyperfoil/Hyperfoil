package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.Phase;
import io.sailrocket.core.util.Util;

public abstract class PhaseBuilder<PB extends PhaseBuilder> {
   protected final String name;
   protected final SimulationBuilder parent;
   protected ScenarioBuilder scenario;
   protected long startTime = -1;
   protected Collection<String> startAfter = new ArrayList<>();
   protected Collection<String> startAfterStrict = new ArrayList<>();
   protected Collection<String> terminateAfterStrict = new ArrayList<>();
   protected long duration = -1;
   protected long maxDuration = -1;

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
      if (scenario != null) {
         throw new BenchmarkDefinitionException("Scenario for " + name + " already set!");
      }
      scenario = new ScenarioBuilder(this);
      return scenario;
   }

   public PB startTime(long startTime) {
      this.startTime = startTime;
      return (PB) this;
   }

   public PB startTime(String startTime) {
      return startTime(Util.parseToMillis(startTime));
   }

   public PB startAfter(String phase) {
      this.startAfter.add(phase);
      return (PB) this;
   }

   public PB startAfterStrict(String phase) {
      this.startAfterStrict.add(phase);
      return (PB) this;
   }

   public PB duration(long duration) {
      this.duration = duration;
      return (PB) this;
   }

   public PB duration(String duration) {
      return duration(Util.parseToMillis(duration));
   }

   public PB maxDuration(long maxDuration) {
      this.maxDuration = maxDuration;
      return (PB) this;
   }

   public PB maxDuration(String duration) {
      return maxDuration(Util.parseToMillis(duration));
   }

   public abstract Phase build();

   public abstract PhaseBuilder<PB> fork(String name);

   public void copyScenarioTo(ScenarioBuilder builder) {
      builder.readFrom(scenario);
   }

   public void slice(PB source, double ratio) {
      // slicing basic timing is just a copy
      startTime = source.startTime;
      startAfter = new ArrayList<>(source.startAfter);
      startAfterStrict = new ArrayList<>(source.startAfterStrict);
      duration = source.duration;
      maxDuration = source.maxDuration;
   }

   protected int sliceValue(int value, double ratio) {
      double sliced = value * ratio;
      long rounded = Math.round(sliced);
      if (Math.abs(rounded - sliced) > 0.0001) {
         throw new BenchmarkDefinitionException("Cannot slice phase " + name + " cleanly: " + value + " * " + ratio + " is not an integer.");
      }
      return (int) rounded;
   }

   public static class AtOnce extends PhaseBuilder<AtOnce> {
      private int users;

      protected AtOnce(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      public AtOnce users(int users) {
         this.users = users;
         return this;
      }

      @Override
      public Phase.AtOnce build() {
         return new Phase.AtOnce(name, scenario.build(), startTime, startAfter, startAfterStrict, terminateAfterStrict, duration, maxDuration, users);
      }

      @Override
      public PhaseBuilder<AtOnce> fork(String name) {
         return new AtOnce(parent, name, -1);
      }

      @Override
      public void slice(AtOnce source, double ratio) {
         super.slice(source, ratio);
         users = sliceValue(source.users, ratio);
      }
   }

   public static class Always extends PhaseBuilder<Always> {
      private int users;

      protected Always(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      @Override
      public Phase.Always build() {
         return new Phase.Always(name, scenario.build(), startTime, startAfter, startAfterStrict,
               terminateAfterStrict, duration, maxDuration, users);
      }

      @Override
      public PhaseBuilder<Always> fork(String name) {
         return new Always(parent, name, -1);
      }

      @Override
      public void slice(Always source, double ratio) {
         super.slice(source, ratio);
         users = sliceValue(source.users, ratio);
      }

      public Always users(int users) {
         this.users = users;
         return this;
      }
   }

   public static class RampPerSec extends PhaseBuilder<RampPerSec> {
      private double initialUsersPerSec;
      private double targetUsersPerSec;
      private int maxSessionsEstimate;

      protected RampPerSec(SimulationBuilder parent, String name, double initialUsersPerSec, double targetUsersPerSec) {
         super(parent, name);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      public RampPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase.RampPerSec build() {
         return new Phase.RampPerSec(name, scenario.build(), startTime, startAfter, startAfterStrict,
               terminateAfterStrict, duration, maxDuration, initialUsersPerSec, targetUsersPerSec,
               maxSessionsEstimate <= 0 ? (int) Math.ceil(Math.max(initialUsersPerSec, targetUsersPerSec)) : maxSessionsEstimate);
      }

      @Override
      public PhaseBuilder<RampPerSec> fork(String name) {
         return new RampPerSec(parent, name, -1, -1);
      }

      @Override
      public void slice(RampPerSec source, double ratio) {
         super.slice(source, ratio);
         initialUsersPerSec = source.initialUsersPerSec * ratio;
         targetUsersPerSec = source.targetUsersPerSec * ratio;
         maxSessionsEstimate = sliceValue(source.maxSessionsEstimate, ratio);
      }

      public RampPerSec initialUsersPerSec(int initialUsersPerSec) {
         this.initialUsersPerSec = initialUsersPerSec;
         return this;
      }

      public RampPerSec targetUsersPerSec(int targetUsersPerSec) {
         this.targetUsersPerSec = targetUsersPerSec;
         return this;
      }
   }

   public static class ConstantPerSec extends PhaseBuilder<ConstantPerSec> {
      private double usersPerSec;
      private int maxSessionsEstimate;

      protected ConstantPerSec(SimulationBuilder parent, String name, double usersPerSec) {
         super(parent, name);
         this.usersPerSec = usersPerSec;
      }

      public ConstantPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase.ConstantPerSec build() {
         return new Phase.ConstantPerSec(name, scenario.build(), startTime, startAfter, startAfterStrict,
               terminateAfterStrict, duration, maxDuration, usersPerSec,
               maxSessionsEstimate <= 0 ? (int) Math.ceil(usersPerSec) : maxSessionsEstimate);
      }

      @Override
      public PhaseBuilder<ConstantPerSec> fork(String name) {
         return new ConstantPerSec(parent, name, -1);
      }

      @Override
      public void slice(ConstantPerSec source, double ratio) {
         super.slice(source, ratio);
         usersPerSec = source.usersPerSec * ratio;
         maxSessionsEstimate = sliceValue(source.maxSessionsEstimate, ratio);
      }

      public ConstantPerSec usersPerSec(int usersPerSec) {
         this.usersPerSec = usersPerSec;
         return this;
      }
   }

   public static class Noop extends PhaseBuilder<Noop> {
      protected Noop(SimulationBuilder parent, String name) {
         super(parent, name);
      }

      @Override
      public Phase build() {
         return new Phase.Noop(name, startAfter, startAfterStrict, terminateAfterStrict);
      }

      @Override
      public PhaseBuilder<Noop> fork(String name) {
         throw new UnsupportedOperationException();
      }
   }

   public static class Discriminator {
      private final SimulationBuilder parent;
      private final String name;

      public Discriminator(SimulationBuilder parent, String name) {
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
