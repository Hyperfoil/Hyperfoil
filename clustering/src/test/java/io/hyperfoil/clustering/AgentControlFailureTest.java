package io.hyperfoil.clustering;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.internal.Properties;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A control message that the agent cannot apply must fail the run.
 * <p>
 * Control messages are published, so nobody waits for a reply. An exception thrown by the handler is not delivered to
 * {@code vertx.exceptionHandler} either, so it used to vanish without a trace: the agent kept running and the
 * controller kept waiting for a phase that would never change status.
 */
public class AgentControlFailureTest {
   private static final long TIMEOUT_MS = 20_000;

   private Vertx vertx;
   private TestController controller;

   @BeforeEach
   public void before() throws Exception {
      vertx = Vertx.vertx();
      Codecs.register(vertx);
      controller = new TestController();
      JsonObject config = new JsonObject()
            .put(Properties.CONTROLLER_HOST, "127.0.0.1")
            .put(Properties.CONTROLLER_PORT, 0);
      vertx.deployVerticle(controller, new DeploymentOptions().setConfig(config))
            .toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   @AfterEach
   public void after() throws Exception {
      vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   @Test
   public void shouldFailTheRunWhenTheAgentCannotApplyAControlMessage() throws Exception {
      // The phase is an hour away, so the controller itself sends nothing during this test.
      Run run = startRun(benchmark());
      awaitOnEventLoop("the agent to be ready",
            () -> run.agents.stream().allMatch(a -> a.status == AgentInfo.Status.READY));

      vertx.eventBus().publish(Feeds.control(run.id),
            new PhaseControlMessage(PhaseControlMessage.Command.RUN, "no-such-phase", null));

      // Other errors may be recorded as well (e.g. the CPU watchdog on a busy machine); look for ours.
      awaitOnEventLoop("the failure to be reported for the unknown phase",
            () -> run.errors.stream().anyMatch(e -> e.error.getMessage().contains("no-such-phase")));
      awaitOnEventLoop("the run to be stopped", () -> run.terminateTime.future().isComplete());
   }

   private Run startRun(Benchmark benchmark) throws Exception {
      CompletableFuture<Run> started = new CompletableFuture<>();
      controller.ctx().runOnContext(nil -> {
         try {
            Run run = controller.createRun(benchmark, null);
            String error = controller.startBenchmark(run);
            if (error != null) {
               started.completeExceptionally(new AssertionError("Cannot start " + benchmark.name() + ": " + error));
            } else {
               started.complete(run);
            }
         } catch (Throwable t) {
            started.completeExceptionally(t);
         }
      });
      return started.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   /** A benchmark without agents, hence started in-VM, whose only phase starts an hour from now. */
   private static Benchmark benchmark() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("agent-control-failure");
      builder.addPhase("main").always(1).startTime(TimeUnit.HOURS.toMillis(1)).duration(1000)
            .scenario()
            .initialSequence("sequence")
            .step(StepCatalog.SC).thinkTime(1, TimeUnit.MILLISECONDS)
            .endStep()
            .endSequence();
      return builder.build();
   }

   /** Polls on the controller's event loop, the thread that mutates the runs. */
   private void awaitOnEventLoop(String what, BooleanSupplier condition) throws InterruptedException {
      CountDownLatch satisfied = new CountDownLatch(1);
      long[] timerId = new long[1];
      controller.ctx().runOnContext(nil -> timerId[0] = vertx.setPeriodic(20, id -> {
         if (condition.getAsBoolean()) {
            satisfied.countDown();
         }
      }));
      try {
         if (!satisfied.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out after " + TIMEOUT_MS + "ms waiting for " + what);
         }
      } finally {
         controller.ctx().runOnContext(nil -> vertx.cancelTimer(timerId[0]));
      }
   }

   private static class TestController extends ControllerVerticle {
      Context ctx() {
         return context;
      }
   }
}
