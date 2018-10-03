package io.sailrocket.core.api;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.sailrocket.api.session.Session;

public interface SimulationRunner {

   void init(BiConsumer<String, PhaseInstance.Status> phaseChangeHook);

   void visitSessions(Consumer<Session> consumer);

   void startPhase(String phase);

   void finishPhase(String phase);

   void tryTerminatePhase(String phase);

   void terminatePhase(String phase);

   void shutdown();
}
