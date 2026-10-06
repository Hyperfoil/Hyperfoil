package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.hyperfoil.controller.StatisticsStore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Regression tests for the run-completion and agent-teardown paths in {@link ControllerVerticle}.
 */
public class ControllerLifecycleTest {
   private static final long TIMEOUT_MS = 10_000;
   private static final Agent AGENT = new Agent("agent-0", null, Map.of());

   private Vertx vertx;
   private CountingController controller;

   @BeforeEach
   public void before() {
      vertx = Vertx.vertx();
      controller = new CountingController();
   }

   @AfterEach
   public void after() throws Exception {
      vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   /**
    * Because {@code checkAgentsStopped} is called both on every STOP reply and after the loop that sends them,
    * multiple invocations can concurrently observe that all agents have terminated. Without an idempotency guard,
    * this triggers the persistence logic twice, causing duplicate post-hook executions and data races when writing
    * to {@code stats/*.csv}, {@code info.json}, and {@code all.json}.
    */
   @Test
   public void shouldPersistTheRunOnlyOnce(@TempDir Path dir) {
      controller.init(vertx, vertx.getOrCreateContext());
      Run run = runWithAgents(dir, AgentInfo.Status.STOPPED);

      controller.checkAgentsStopped(run);
      controller.checkAgentsStopped(run);

      assertEquals(1, controller.persisted.get(), "the run must be persisted exactly once");
   }

   /**
    * A premature call to {@code checkAgentsStopped} must leave the completion guard open,
    * allowing the run to be persisted once the final agent actually stops.
    */
   @Test
   public void shouldStillPersistOnceTheLastAgentStops(@TempDir Path dir) {
      controller.init(vertx, vertx.getOrCreateContext());
      Run run = runWithAgents(dir, AgentInfo.Status.STOPPING);

      controller.checkAgentsStopped(run);
      assertEquals(0, controller.persisted.get(), "not every agent is terminal yet");

      run.agents.get(0).status = AgentInfo.Status.STOPPED;
      controller.checkAgentsStopped(run);
      assertEquals(1, controller.persisted.get());
   }

   /**
    * When a run starts, a timer is set to fail the run if agents take too long to connect.
    * If a run is aborted early, this timer used to be left running, eventually throwing a
    * "Deployment timed out" error for a run that was already dead.
    *
    * This test ensures that aborting a run cleanly cancels this deployment timer.
    */
   @Test
   public void shouldCancelTheDeployTimeoutWhenTheRunIsAborted(@TempDir Path dir) throws Exception {
      controller.init(vertx, vertx.getOrCreateContext());
      Run run = new Run("0000000000000000", dir, Benchmark.forTesting());
      run.statsSupplier = () -> new StatisticsStore(run.benchmark, f -> {
      });

      AtomicBoolean timerStillExisted = new AtomicBoolean(true);
      CountDownLatch done = new CountDownLatch(1);

      vertx.runOnContext(nil -> {
         // 1. Set a 10-second timer. It will definitely not fire during this fast test.
         run.deployTimerId = vertx.setTimer(10000, id -> {
         });

         // 2. Abort the run. This is the method we are testing; it should cancel the timer.
         controller.stopSimulation(run);

         // 3. Try to cancel it again ourselves. If stopSimulation did its job,
         // this will return 'false' because the timer was already removed.
         boolean canceledByUs = vertx.cancelTimer(run.deployTimerId);
         timerStillExisted.set(canceledByUs);

         done.countDown();
      });

      assertTrue(done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
      assertFalse(timerStillExisted.get(), "The deploy timer should have already been canceled by stopSimulation");
   }

   /**
    * Verifies that if the controller tries to shut down an agent and the agent doesn't respond,
    * that failure is formally recorded in the run's final report ({@code run.errors}), rather than
    * just silently printing to the server log and being forgotten.
    * <p>
    * In practice, this happens when multiple agents crash simultaneously: the first crash aborts the
    * run and marks the others as STOPPING, causing their own crash callbacks to be ignored. A failed
    * STOP message becomes the only remaining trace that the agent actually died.
    */
   @Test
   public void shouldRecordAnErrorWhenAnAgentFailsToStop(@TempDir Path dir) throws Exception {
      controller.init(vertx, vertx.getOrCreateContext());
      Run run = runWithAgents(dir, AgentInfo.Status.REGISTERED);
      // Nothing is listening, so the STOP fails with NO_HANDLERS - delivered inline, before request() returns
      run.agents.get(0).deploymentId = "no-such-agent";

      runOnContextAndWait(() -> controller.stopSimulation(run));
      awaitOnEventLoop("an error to be recorded", () -> !run.errors.isEmpty());

      assertEquals(1, run.errors.size());
      assertEquals("Agent failed to stop", run.errors.get(0).error.getMessage());
      assertEquals("agent-0", run.errors.get(0).agent.name);
   }

   /**
    * Ensures we do not leave "zombie" agents running forever.
    * <p>
    * Sometimes an agent error aborts the run *before* the agent process is fully created.
    * When the agent process finally appears a moment later, the run is already over.
    * This test verifies that the controller catches this late agent and stops it immediately.
    */
   @Test
   public void shouldStopAnAgentThatFinishedDeployingAfterTheRunEnded(@TempDir Path dir) throws Exception {
      AtomicBoolean stopped = new AtomicBoolean();
      Run run = runWithAgents(dir, AgentInfo.Status.STARTING);
      controller.init(vertx, vertx.getOrCreateContext());
      controller.setDeployer(new FakeDeployer((agent, exceptionHandler) -> () -> stopped.set(true)));

      // The run is already over - stopSimulation walked run.agents while deployedAgent was still null and
      // found nothing to tear down - and only now does the deployer hand back a live agent.
      runOnContextAndWait(() -> controller.stopSimulation(run));
      runOnContextAndWait(() -> controller.deployAgent(run, run.agents.get(0), AGENT));

      awaitOnEventLoop("the late agent to be stopped", stopped::get);
   }

   /**
    * Deployments happen on background worker threads, but controller state (like 'run.errors')
    * must only be modified on the Vert.x event loop to avoid race conditions.
    *
    * This test ensures that if a deployment fails, the background thread doesn't modify
    * the controller state directly. We prove this by triggering an error from a custom
    * background thread and verifying that the error isn't added instantly, but is instead
    * safely scheduled for the event loop to handle shortly after.
    */
   @Test
   public void shouldApplyADeployerFailureOnTheEventLoop(@TempDir Path dir) throws Exception {
      AtomicReference<Consumer<Throwable>> captured = new AtomicReference<>();
      CountDownLatch handlerReady = new CountDownLatch(1);
      Deployer deployer = new FakeDeployer((agent, exceptionHandler) -> {
         captured.set(exceptionHandler);
         handlerReady.countDown();
         return () -> {
         };
      });
      controller.init(vertx, vertx.getOrCreateContext());
      controller.setDeployer(deployer);
      Run run = runWithAgents(dir, AgentInfo.Status.STARTING);

      runOnContextAndWait(() -> controller.deployAgent(run, run.agents.get(0), AGENT));
      assertTrue(handlerReady.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "deployer was never called");

      // Call it from a thread that is not the event loop, which is what a deployer does, and have that same
      // thread report what it saw. Reading run.errors from here instead would race the event loop, which is
      // free to have applied the failure already by the time join() returns.
      AtomicBoolean appliedOnCallerThread = new AtomicBoolean();
      Thread caller = new Thread(() -> {
         captured.get().accept(new RuntimeException("agent process died"));
         appliedOnCallerThread.set(!run.errors.isEmpty());
      });
      caller.start();
      caller.join(TIMEOUT_MS);

      assertFalse(appliedOnCallerThread.get(),
            "the failure must be applied on the event loop, not on the deployer's thread");
      awaitOnEventLoop("the failure to be applied", () -> !run.errors.isEmpty());
      assertEquals("Failed to deploy agent", run.errors.get(0).error.getMessage());
   }

   private Run runWithAgents(Path dir, AgentInfo.Status status) {
      Run run = new Run("0000000000000000", dir, Benchmark.forTesting());
      run.statsSupplier = () -> new StatisticsStore(run.benchmark, f -> {
      });
      AgentInfo agent = new AgentInfo("agent-0", 0);
      agent.status = status;
      run.agents.add(agent);
      return run;
   }

   private void runOnContextAndWait(Runnable action) throws Exception {
      CompletableFuture<Void> done = new CompletableFuture<>();
      vertx.runOnContext(nil -> {
         try {
            action.run();
            done.complete(null);
         } catch (Throwable t) {
            done.completeExceptionally(t);
         }
      });
      done.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   /**
    * Polls on the event loop, which is the thread the controller mutates {@code run} from - a plain read from
    * the test thread shares no happens-before edge with those writes.
    */
   private void awaitOnEventLoop(String what, java.util.function.BooleanSupplier condition) {
      CountDownLatch satisfied = new CountDownLatch(1);
      long timerId = vertx.setPeriodic(20, id -> {
         if (condition.getAsBoolean()) {
            satisfied.countDown();
         }
      });
      try {
         if (!satisfied.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out after " + TIMEOUT_MS + "ms waiting for " + what);
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new AssertionError("Interrupted waiting for " + what, e);
      } finally {
         vertx.cancelTimer(timerId);
      }
   }

   /** Counts how often the run was handed over to be written, without writing anything. */
   private static class CountingController extends ControllerVerticle {
      final AtomicInteger persisted = new AtomicInteger();

      @Override
      void persistRun(Run run) {
         persisted.incrementAndGet();
         run.persisted = true;
      }
   }

   private interface StartAgent {
      DeployedAgent start(Agent agent, Consumer<Throwable> exceptionHandler);
   }

   private static class FakeDeployer implements Deployer {
      private final StartAgent onStart;

      FakeDeployer(StartAgent onStart) {
         this.onStart = onStart;
      }

      @Override
      public DeployedAgent start(Agent agent, String runId, Benchmark benchmark, Consumer<Throwable> exceptionHandler) {
         return onStart.start(agent, exceptionHandler);
      }

      @Override
      public boolean hasControllerLog() {
         return false;
      }

      @Override
      public void downloadControllerLog(long offset, long maxLength, String destinationFile,
            Handler<AsyncResult<Void>> handler) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void downloadAgentLog(DeployedAgent deployedAgent, long offset, long maxLength, String destinationFile,
            Handler<AsyncResult<Void>> handler) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void close() {
      }
   }
}
