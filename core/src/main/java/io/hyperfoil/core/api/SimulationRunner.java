package io.hyperfoil.core.api;

import java.util.function.Consumer;

import io.hyperfoil.api.session.Session;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface SimulationRunner {

   void init();

   void openConnections(Handler<AsyncResult<Void>> handler);

   void visitSessions(Consumer<Session> consumer);

   void startPhase(String phase);

   void finishPhase(String phase);

   void tryTerminatePhase(String phase);

   void terminatePhase(String phase);

   void shutdown();

}
