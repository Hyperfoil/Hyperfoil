package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertNull;

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
}
