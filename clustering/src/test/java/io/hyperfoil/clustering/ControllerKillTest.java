package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.NotSerializableException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.controller.StatisticsStore;
import io.hyperfoil.core.builders.StepCatalog;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

/**
 * Killing a run sends {@code TERMINATE} to the agents. As for every other control message, a failure to put it on the
 * wire must fail the run instead of being lost: otherwise the phases that were not reached keep running and the
 * run reports numbers from a simulation that was not stopped the way the user asked.
 */
public class ControllerKillTest {

   @Test
   public void shouldAbortTheRunWhenTerminateCannotBeSent(@TempDir Path dir) {
      EventBus eb = mock(EventBus.class);
      doThrow(new UncheckedIOException("Cannot serialize", new NotSerializableException("java.lang.Object")))
            .when(eb).publish(anyString(), any());
      Vertx vertx = mock(Vertx.class);
      when(vertx.eventBus()).thenReturn(eb);

      ControllerVerticle controller = new ControllerVerticle() {
         @Override
         void persistRun(Run run) {
            run.persisted = true;
         }
      };
      controller.init(vertx, mock(Context.class));

      Run run = new Run("0000000000000000", dir, benchmark());
      run.statsSupplier = () -> new StatisticsStore(run.benchmark, f -> {
      });
      run.benchmark.phases().forEach(phase -> {
         ControllerPhase controllerPhase = new ControllerPhase(phase);
         controllerPhase.status(run.id, ControllerPhase.Status.RUNNING);
         run.phases.put(phase.name(), controllerPhase);
      });

      AtomicReference<AsyncResult<Void>> result = new AtomicReference<>();
      controller.kill(run, result::set);

      assertEquals(1, run.errors.size(), "the failure must be recorded in the run");
      assertTrue(run.errors.get(0).error.getMessage().contains("TERMINATE for phase main"),
            run.errors.get(0).error.getMessage());
      assertTrue(run.terminateTime.future().isComplete(), "the run must be stopped, not left running");
      assertTrue(result.get() != null && result.get().succeeded(), "the caller of kill must see the run end");
   }

   private static Benchmark benchmark() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("kill-test");
      builder.addPhase("main").always(1).duration(1000)
            .scenario()
            .initialSequence("sequence")
            .step(StepCatalog.SC).thinkTime(1, TimeUnit.MILLISECONDS)
            .endStep()
            .endSequence();
      return builder.build();
   }
}
