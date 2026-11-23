package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.internal.Properties;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Two runs on the same controller must not see each other's phase control messages.
 * <p>
 * The controller publishes {@code RUN}/{@code FINISH}/{@code TRY_TERMINATE}/{@code TERMINATE} on
 * the control feed, which used to be a single address delivered to every agent registered on the event bus regardless of the
 * run it belongs
 * to. The message carries a phase name but neither a run id nor an agent id and the agent does not filter.
 * <p>
 * Both runs here are started in-VM (the benchmarks define no agents), so each run has exactly one agent and there is
 * no way to blame a shared agent name.
 */
public class ConcurrentRunsControlFeedTest {
   private static final long TIMEOUT_MS = 20_000;
   /** Phase of run A that runs right away. */
   private static final String PHASE = "main";

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

   /**
    * Run B has a phase with the same name as run A's, but scheduled an hour from now, so the controller never asks
    * B's agent to run it. Run A's {@code RUN main} must not start it.
    * <p>
    * Observable on the controller: the agent reports every phase change tagged with its own run id, so if B's agent
    * ran {@code main} B's {@link AgentInfo#phases} would get an entry for it.
    */
   @Test
   public void shouldNotStartAPhaseOfAnotherRunWithTheSameName() throws Exception {
      Run b = startRun(benchmark("run-b", PHASE, TimeUnit.HOURS.toMillis(1)));
      awaitAgentReady(b);
      Run a = startRun(benchmark("run-a", PHASE, 0));

      awaitOnEventLoop("run A to run its phase to completion",
            () -> a.phases.get(PHASE) != null && a.phases.get(PHASE).status().isTerminated());

      // Run A's phase lasts a second and went through every status on the agent; had run B's agent received the
      // same RUN it would have reported at least STARTING long before that.
      CompletableFuture<Object> seen = new CompletableFuture<>();
      controller.ctx().runOnContext(nil -> seen.complete(b.agents.get(0).phases.get(PHASE)));
      assertNull(seen.get(TIMEOUT_MS, TimeUnit.MILLISECONDS),
            "Run B's agent ran phase '" + PHASE + "' although run B never asked for it: "
                  + "run A's control message was delivered to it");
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

   /**
    * The agent subscribes to the control feed when it is initialized; run A must not start before that, or run B
    * would miss the message for a reason that has nothing to do with the bug.
    */
   private void awaitAgentReady(Run run) throws InterruptedException {
      awaitOnEventLoop("the agent of " + run.id + " to be ready",
            () -> run.agents.stream().allMatch(a -> a.status == AgentInfo.Status.READY));
   }

   /**
    * A benchmark without agents, hence started in-VM, with a single phase of one second. The phase is an
    * {@code always} one so that the duration is honoured: run A has to outlive the delivery of its control messages to
    * run B's agent, which is the only thing this test cannot wait for.
    */
   private static Benchmark benchmark(String name, String phase, long startTime) {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name(name);
      builder.addPhase(phase).always(1).startTime(startTime).duration(1000)
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
