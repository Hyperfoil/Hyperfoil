package io.hyperfoil.api.session;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.hyperfoil.api.config.Phase;

public interface ControllerListener {
   CompletableFuture<Void> onPhaseChange(Phase phase, PhaseInstance.Status status, boolean sessionLimitExceeded, Throwable error, Map<String, GlobalData.Element> globalData);
}
