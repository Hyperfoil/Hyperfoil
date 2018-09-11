package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import io.sailrocket.api.Phase;
import io.sailrocket.api.Scenario;

public abstract class PhaseBuilder<PB extends PhaseBuilder> {
   protected final String name;
   protected final SimulationBuilder parent;
   protected Scenario scenario;
   protected long startTime = -1;
   protected Collection<String> startAfter = new ArrayList<>();
   protected Collection<String> startAfterStrict = new ArrayList<>();
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

   public PB scenario(Scenario scenario) {
      this.scenario = scenario;
      return (PB) this;
   }

   public PB scenario(ScenarioBuilder builder) {
      this.scenario = builder.build();
      return (PB) this;
   }

   public PB startTime(long startTime) {
      this.startTime = startTime;
      return (PB) this;
   }

   public PB startTime(String startTime) {
      return startTime(parseDuration(startTime));
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
      return duration(parseDuration(duration));
   }

   public PB maxDuration(long maxDuration) {
      this.maxDuration = maxDuration;
      return (PB) this;
   }

   public PB maxDuration(String duration) {
      return maxDuration(parseDuration(duration));
   }

   public abstract Phase build();

   private static long parseDuration(String s) {
      TimeUnit unit;
      String prefix;
      switch (s.charAt(s.length() - 1)) {
         case 's':
            unit = TimeUnit.SECONDS;
            prefix = s.substring(0, s.length() - 1);
            break;
         case 'm':
            unit = TimeUnit.MINUTES;
            prefix = s.substring(0, s.length() - 1);
            break;
         case 'h':
            unit = TimeUnit.HOURS;
            prefix = s.substring(0, s.length() - 1);
            break;
         default:
            unit = TimeUnit.SECONDS;
            prefix = s;
            break;
      }
      return unit.toMillis(Long.parseLong(prefix));
   }

   public static class AtOnce extends PhaseBuilder<AtOnce> {
      private final int users;

      protected AtOnce(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      @Override
      public Phase build() {
         return new Phase.AtOnce(name, scenario, startTime, parent.getPhases(startAfter), parent.getPhases(startAfterStrict), duration, maxDuration, users);
      }
   }

   public static class Always extends PhaseBuilder<Always> {
      private final int users;

      protected Always(SimulationBuilder parent, String name, int users) {
         super(parent, name);
         this.users = users;
      }

      @Override
      public Phase build() {
         return new Phase.Always(name, scenario, startTime, parent.getPhases(startAfter), parent.getPhases(startAfterStrict), duration, maxDuration, users);
      }
   }

   public static class RampPerSec extends PhaseBuilder {
      private final int initialUsersPerSec;
      private final int targetUsersPerSec;
      private int maxSessionsEstimate;

      protected RampPerSec(SimulationBuilder parent, String name, int initialUsersPerSec, int targetUsersPerSec) {
         super(parent, name);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      public RampPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase build() {
         return new Phase.RampPerSec(name, scenario, startTime, parent.getPhases(startAfter),
               parent.getPhases(startAfterStrict), duration, maxDuration, initialUsersPerSec, targetUsersPerSec,
               maxSessionsEstimate <= 0 ? Math.max(initialUsersPerSec, targetUsersPerSec) : maxSessionsEstimate);
      }
   }

   public static class ConstantPerSec extends PhaseBuilder {
      private final int usersPerSec;
      private int maxSessionsEstimate;

      protected ConstantPerSec(SimulationBuilder parent, String name, int usersPerSec) {
         super(parent, name);
         this.usersPerSec = usersPerSec;
      }

      public ConstantPerSec maxSessionsEstimate(int maxSessionsEstimate) {
         this.maxSessionsEstimate = maxSessionsEstimate;
         return this;
      }

      @Override
      public Phase build() {
         return new Phase.ConstantPerSec(name, scenario, startTime, parent.getPhases(startAfter),
               parent.getPhases(startAfterStrict), duration, maxDuration, usersPerSec,
               maxSessionsEstimate <= 0 ? usersPerSec : maxSessionsEstimate);
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
