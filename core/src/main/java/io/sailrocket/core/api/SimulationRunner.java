package io.sailrocket.core.api;

import java.util.function.Consumer;

import io.sailrocket.api.session.PhaseChangeHandler;
import io.sailrocket.api.session.Session;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface SimulationRunner {

   void init(PhaseChangeHandler phaseChangeHook, Handler<AsyncResult<Void>> handler);

   void visitSessions(Consumer<Session> consumer);

   void startPhase(String phase);

   void finishPhase(String phase);

   void tryTerminatePhase(String phase);

   void terminatePhase(String phase);

   void shutdown();

}
