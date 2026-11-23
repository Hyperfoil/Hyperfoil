package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.builders.StepCatalog;
import io.vertx.core.Vertx;

/**
 * {@link ControllerVerticle#startBenchmark(Run)} must refuse a run that needs an agent which another, still active run
 * is already using.
 */
public class AgentInUseGuardTest {
   private static final long TIMEOUT_MS = 10_000;

   private Vertx vertx;
   private ControllerVerticle controller;

   @BeforeEach
   public void before() {
      vertx = Vertx.vertx();
      controller = new ControllerVerticle();
      controller.init(vertx, vertx.getOrCreateContext());
   }

   @AfterEach
   public void after() throws Exception {
      vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   @Test
   public void shouldRejectARunUsingAnAgentOfAnActiveRun(@TempDir Path dir) {
      Run active = run("0001", dir, "agent-0");
      active.agents.add(new AgentInfo("agent-0", 0));
      controller.runs.put(active.id, active);

      Run next = run("0002", dir, "agent-0");
      controller.runs.put(next.id, next);

      String reason = controller.startBenchmark(next);

      assertNotNull(reason, "the run must be refused");
      assertTrue(reason.contains("already used"), "unexpected reason: " + reason);
      assertTrue(next.agents.isEmpty(), "no agent may be started for a refused run");
   }

   @Test
   public void shouldNotRejectARunUsingAnAgentOfACompletedRun(@TempDir Path dir) {
      Run done = run("0001", dir, "agent-0");
      done.agents.add(new AgentInfo("agent-0", 0));
      done.terminateTime.complete(System.currentTimeMillis());
      controller.runs.put(done.id, done);

      Run next = run("0002", dir, "agent-0");
      controller.runs.put(next.id, next);

      String reason = controller.startBenchmark(next);

      // The guard is out of the way; this controller is not clustered so it refuses agents for another reason.
      assertEquals("Server is not started as clustered and does not accept benchmarks with agents defined.", reason);
   }

   private static Run run(String id, Path dir, String agentName) {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("benchmark-" + id)
            .addAgent(agentName, null, Map.of());
      builder.addPhase("main").atOnce(1)
            .scenario()
            .initialSequence("sequence")
            .step(StepCatalog.SC).thinkTime(1, TimeUnit.MILLISECONDS)
            .endStep()
            .endSequence();
      Benchmark benchmark = builder.build();
      return new Run(id, dir.resolve(id), benchmark);
   }
}
