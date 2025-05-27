package io.hyperfoil.api.deployment;

import java.io.Closeable;
import java.util.function.Consumer;

import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.config.Benchmark;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface Deployer extends Closeable {

   DeployedAgent start(Agent agent, String runId, Benchmark benchmark, Consumer<Throwable> exceptionHandler);

   boolean hasControllerLog();

   void downloadControllerLog(long offset, long maxLength, String destinationFile, Handler<AsyncResult<Void>> handler);

   void downloadAgentLog(DeployedAgent deployedAgent, long offset, long maxLength, String destinationFile,
         Handler<AsyncResult<Void>> handler);

   interface Factory {
      String name();

      Deployer create();
   }
}
