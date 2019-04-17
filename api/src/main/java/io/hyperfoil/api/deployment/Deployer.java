package io.hyperfoil.api.deployment;

import java.io.Closeable;
import java.util.function.Consumer;

import io.hyperfoil.api.config.Agent;

public interface Deployer extends Closeable {

   DeployedAgent start(Agent agent, String runId, Consumer<Throwable> exceptionHandler);

   interface Factory {
      String name();

      Deployer create();
   }
}
