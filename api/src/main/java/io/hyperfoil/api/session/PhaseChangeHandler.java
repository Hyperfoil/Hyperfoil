package io.hyperfoil.api.session;

import io.hyperfoil.api.config.Phase;

public interface PhaseChangeHandler {
   void onChange(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error);
}
