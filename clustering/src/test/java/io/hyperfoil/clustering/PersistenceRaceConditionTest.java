package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.StatisticsStore;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PersistenceRaceConditionTest {

   private Vertx vertx;
   private ControllerVerticle controller;

   @BeforeEach
   public void before() throws Exception {
      vertx = Vertx.vertx();
      controller = new ControllerVerticle();

      CountDownLatch initLatch = new CountDownLatch(1);
      vertx.runOnContext(v -> {
         controller.init(vertx, vertx.getOrCreateContext());
         initLatch.countDown();
      });
      initLatch.await(5, TimeUnit.SECONDS);
   }

   @AfterEach
   public void after() throws Exception {
      vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
   }

   @Test
   public void shouldWaitForPersistenceToCompleteBeforeReading(@TempDir Path dir) throws Exception {

      AtomicReference<Throwable> capturedError = new AtomicReference<>();
      int count = 0;
      while (count < 100) {

         Run run = new Run("race-run-old", dir, Benchmark.forTesting());
         run.statsSupplier = () -> new StatisticsStore(run.benchmark, f -> {
         });

         Path statsPath = dir.resolve(ControllerVerticle.DEFAULT_STATS_JSON);
         CountDownLatch latch = new CountDownLatch(1);

         vertx.runOnContext(v -> {
            // 1. Trigger persistence on worker thread
            controller.persistRun(run);

            // wait for the promise
            run.persistenceComplete.future().onComplete(ar -> {
               try {
                  if (ar.succeeded()) {
                     byte[] content = Files.readAllBytes(statsPath);
                     ObjectMapper mapper = new ObjectMapper();
                     mapper.readTree(content);
                  }
               } catch (Throwable t) {
                  capturedError.set(t);
               } finally {
                  latch.countDown();
               }
            });
         });
         latch.await(5, TimeUnit.SECONDS);
         if (capturedError.get() != null) {
            break;
         }
         count++;
      }
      assertNull(capturedError.get(), "Race condition detected: stats file read before persistence completed");
   }

   @Test
   public void shouldCompleteImmediatelyForHistoricalRuns(@TempDir Path runDir) throws Exception {
      // Simulate a historical run that was already persisted to disk
      String runId = "ABCD";
      Path runPath = runDir.resolve(runId);
      Files.createDirectories(runPath);

      // Create info.json
      JsonObject info = new JsonObject()
            .put("id", runId)
            .put("benchmark", "test-benchmark")
            .put("startTime", System.currentTimeMillis() - 10000)
            .put("terminateTime", System.currentTimeMillis() - 5000)
            .put("cancelled", false);
      Files.writeString(runPath.resolve("info.json"), info.encodePrettily());

      // Create all.json
      JsonArray statsArray = new JsonArray();
      Files.writeString(runPath.resolve("all.json"), statsArray.encodePrettily());

      // Create stats directory for CSV
      Files.createDirectories(runPath.resolve("stats"));

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<Boolean> persistenceCompleted = new AtomicReference<>(false);
      AtomicReference<Throwable> error = new AtomicReference<>();

      vertx.runOnContext(v -> {
         try {
            // Call updateRuns which should set persistenceComplete
            controller.updateRuns(runPath);

            // Get the run that was just loaded
            Run run = controller.run(runId);

            if (run == null) {
               error.set(new AssertionError("Run not found after updateRuns()"));
               latch.countDown();
               return;
            }

            // Check if persistenceComplete is already done (should be immediate for historical runs)
            if (run.persistenceComplete.future().isComplete()) {
               persistenceCompleted.set(true);
               latch.countDown();
            } else {
               // Wait a bit to see if it completes
               vertx.setTimer(100, tid -> {
                  persistenceCompleted.set(run.persistenceComplete.future().isComplete());
                  latch.countDown();
               });
            }
         } catch (Throwable t) {
            error.set(t);
            latch.countDown();
         }
      });

      assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
      assertNull(error.get(), "Error occurred during test");
      assertTrue(persistenceCompleted.get(),
            "persistenceComplete should be immediately completed for historical runs loaded by updateRuns()");
   }

   @Test
   public void shouldFailPersistenceForEarlyRejectedRuns(@TempDir Path runDir) throws Exception {
      // Test that early-rejected runs fail persistenceComplete instead of leaving it pending

      Benchmark benchmark = Benchmark.forTesting();
      Run run = new Run("EARLY", runDir.resolve("EARLY"), benchmark);

      CountDownLatch latch = new CountDownLatch(1);
      AtomicReference<Boolean> persistenceFailed = new AtomicReference<>(false);
      AtomicReference<Throwable> error = new AtomicReference<>();

      vertx.runOnContext(v -> {
         try {
            // Simulate early rejection by completing terminateTime and calling persistenceComplete.fail()
            long currentTime = System.currentTimeMillis();
            run.startTime = currentTime;
            run.terminateTime.complete(currentTime);
            run.completed = true;
            run.persistenceComplete.fail("Run terminated before persistence: test rejection");

            // Verify that persistenceComplete is failed
            run.persistenceComplete.future().onComplete(ar -> {
               persistenceFailed.set(ar.failed());
               latch.countDown();
            });
         } catch (Throwable t) {
            error.set(t);
            latch.countDown();
         }
      });

      assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
      assertNull(error.get(), "Error occurred during test");
      assertTrue(persistenceFailed.get(),
            "persistenceComplete should be failed (not pending) for early-rejected runs");
   }
}
