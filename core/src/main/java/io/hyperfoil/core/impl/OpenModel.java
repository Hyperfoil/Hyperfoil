package io.hyperfoil.core.impl;

import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.impl.rate.FireTimeSequence;

final class OpenModel {

   public static PhaseInstance constantRate(Phase def, String runId, int agentId) {
      var model = (Model.ConstantRate) def.model;
      double usersPerSec = def.benchmark().slice(model.usersPerSec, agentId);
      if (model.variance) {
         return new OpenModelPhase(FireTimeSequence.poissonConstantRate(usersPerSec), def, runId, agentId);
      } else {
         return new OpenModelPhase(FireTimeSequence.constantRate(usersPerSec), def, runId, agentId);
      }
   }

   public static PhaseInstance rampRate(Phase def, String runId, int agentId) {
      var model = (Model.RampRate) def.model;
      double initialUsersPerSec = def.benchmark().slice(model.initialUsersPerSec, agentId);
      double targetUsersPerSec = def.benchmark().slice(model.targetUsersPerSec, agentId);
      long durationNs = def.duration * 1_000_000L;
      if (model.variance) {
         return new OpenModelPhase(FireTimeSequence.poissonRampRate(initialUsersPerSec, targetUsersPerSec, durationNs), def,
               runId, agentId);
      } else {
         return new OpenModelPhase(FireTimeSequence.rampRate(initialUsersPerSec, targetUsersPerSec, durationNs), def, runId,
               agentId);
      }
   }
}
