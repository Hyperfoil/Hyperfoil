package io.hyperfoil.api.config;

import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface Model extends Serializable {
   Logger log = LogManager.getLogger(Phase.class);

   String description();

   default void validate(Phase phase) {
   }

   class AtOnce implements Model {
      public final int users;

      public AtOnce(int users) {
         this.users = users;
      }

      @Override
      public String description() {
         return users + " users at once";
      }

      @Override
      public void validate(Phase phase) {
         if (phase.duration > 0) {
            log.warn("Duration for phase {} is ignored.", phase.duration);
         }
      }
   }

   class Always implements Model {
      public final int users;

      public Always(int users) {
         this.users = users;
      }

      @Override
      public String description() {
         return users + " users always";
      }

      @Override
      public void validate(Phase phase) {
         if (phase.duration < 0) {
            throw new BenchmarkDefinitionException("Duration was not set for phase '" + phase.name + "'");
         }
      }

   }

   abstract class OpenModel implements Model {
      public final boolean variance;
      public final int maxSessions;
      public final SessionLimitPolicy sessionLimitPolicy;

      public OpenModel(boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         this.variance = variance;
         this.maxSessions = maxSessions;
         this.sessionLimitPolicy = sessionLimitPolicy;
      }

      @Override
      public void validate(Phase phase) {
         if (phase.duration < 0) {
            throw new BenchmarkDefinitionException("Duration was not set for phase '" + phase.name + "'");
         }
      }
   }

   class RampRate extends OpenModel {
      public final double initialUsersPerSec;
      public final double targetUsersPerSec;

      public RampRate(double initialUsersPerSec, double targetUsersPerSec,
                      boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         super(variance, maxSessions, sessionLimitPolicy);
         this.initialUsersPerSec = initialUsersPerSec;
         this.targetUsersPerSec = targetUsersPerSec;
      }

      @Override
      public String description() {
         return String.format("%.2f - %.2f users per second", initialUsersPerSec, targetUsersPerSec);
      }
   }

   class ConstantRate extends OpenModel {
      public final double usersPerSec;

      public ConstantRate(double usersPerSec, boolean variance, int maxSessions, SessionLimitPolicy sessionLimitPolicy) {
         super(variance, maxSessions, sessionLimitPolicy);
         this.usersPerSec = usersPerSec;
      }

      @Override
      public String description() {
         return String.format("%.2f users per second", usersPerSec);
      }
   }

   class Sequentially implements Model {
      public final int repeats;

      public Sequentially(int repeats) {
         this.repeats = repeats;
      }

      @Override
      public String description() {
         return repeats + " times";
      }
   }

   class Noop implements Model {
      @Override
      public String description() {
         return "No-op phase";
      }
   }
}
