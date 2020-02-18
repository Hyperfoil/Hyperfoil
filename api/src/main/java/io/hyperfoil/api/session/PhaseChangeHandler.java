package io.hyperfoil.api.session;

import java.util.concurrent.CompletableFuture;

import io.hyperfoil.api.config.Phase;

public interface PhaseChangeHandler {
   CompletableFuture<Void> onChange(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error);
}
