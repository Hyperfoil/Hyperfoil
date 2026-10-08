package io.hyperfoil.benchmark.clustering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Agent;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.hyperfoil.internal.Properties;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

/**
 * Kills an agent JVM with SIGKILL during a run to ensure the controller detects it and fails the run promptly.
 */
@Tag("io.hyperfoil.test.Benchmark")
@EnabledOnOs(value = OS.LINUX, disabledReason = "Relies on pgrep and kill -9 to take down the agent JVM")
public class AgentCrashTest extends BaseClusteredTest {

   private static final String SURVIVOR = "survivor";
   private static final String DOOMED = "doomed";
   private static final long PHASE_DURATION_MS = 120_000;
   private static final long DETECTION_DEADLINE_MS = 60_000;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      startController(ctx);
   }

   @Test
   public void shouldFailTheRunWhenAnAgentIsKilled() throws Exception {
      boolean executed = false;
      //@formatter:off
      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
            .name("agent-crash")
            .addAgent(SURVIVOR, "localhost", null)
            .addAgent(DOOMED, "localhost", null)
            .addPlugin(HttpPluginBuilder::new)
               .http()
                  .host("localhost").port(httpServer.actualPort())
                  .sharedConnections(4)
               .endHttp()
            .endPlugin()
            .addPhase("test").always(2)
               .duration(PHASE_DURATION_MS)
               .scenario()
                  .initialSequence("test")
                     .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET).path("/").endStep()
                  .endSequence()
               .endScenario()
            .endPhase();
      //@formatter:on

      try (RestClient client = new RestClient(vertx, "localhost", controllerPort, false, false, null)) {
         Client.BenchmarkRef ref = client.register(benchmark.build(), null);
         Client.RunRef run = ref.start(null, Collections.emptyMap());
         try {
            awaitAgentsRunning(run);
            long pid = killAgent(run.id(), DOOMED);
            log.info("Killed agent {} (pid {}) of run {}", DOOMED, pid, run.id());

            Run info = awaitCompleted(run);
            assertThat(info.errors)
                  .withFailMessage("The run ended without blaming %s: %s", DOOMED, info.errors)
                  .anySatisfy(error -> assertThat(error).startsWith(DOOMED + ":"));
            // Survivor should stop cleanly, killed agent must be marked FAILED
            assertThat(agent(info, SURVIVOR).status)
                  .withFailMessage("%s did not shut down cleanly, so the run was not torn down properly", SURVIVOR)
                  .isEqualTo("STOPPED");
            assertThat(agent(info, DOOMED).status)
                  .withFailMessage("%s was killed but the run reports it as %s", DOOMED, agent(info, DOOMED).status)
                  .isEqualTo("FAILED");
            executed = true;
         } finally {
            if (!run.get().completed) {
               run.kill();
            }
         }
      }
      assertTrue(executed);
   }

   private void awaitAgentsRunning(Client.RunRef run) throws InterruptedException {
      long deadline = System.currentTimeMillis() + DETECTION_DEADLINE_MS;
      while (System.currentTimeMillis() < deadline) {
         Run info = run.get();
         if (info.started != null && info.agents.size() == 2
               && info.agents.stream().noneMatch(agent -> "STARTING".equals(agent.status))) {
            return;
         }
         if (info.completed) {
            throw new AssertionError("The run finished before the agents were up: " + info.errors);
         }
         Thread.sleep(100);
      }
      throw new AssertionError("Agents did not start within " + DETECTION_DEADLINE_MS + " ms: " + run.get().agents);
   }

   private Run awaitCompleted(Client.RunRef run) throws InterruptedException {
      long deadline = System.currentTimeMillis() + DETECTION_DEADLINE_MS;
      while (System.currentTimeMillis() < deadline) {
         Run info = run.get();
         if (info.completed) {
            return info;
         }
         Thread.sleep(100);
      }
      throw new AssertionError("The run did not terminate within " + DETECTION_DEADLINE_MS
            + " ms of the agent being killed; the controller did not react to it leaving the cluster");
   }

   private static Agent agent(Run info, String name) {
      return info.agents.stream().filter(agent -> name.equals(agent.name)).findFirst()
            .orElseThrow(() -> new AssertionError("No agent named " + name + " in " + info.agents));
   }

   private static long killAgent(String runId, String name) throws IOException, InterruptedException {
      List<String> agents = exec("pgrep", "-af", Properties.AGENT_NAME + "=");
      List<String> matches = agents.stream()
            .filter(line -> hasProperty(line, Properties.AGENT_NAME, name))
            .filter(line -> hasProperty(line, Properties.RUN_ID, runId))
            .collect(Collectors.toList());
      assertThat(matches)
            .withFailMessage("Expected exactly one JVM for agent %s of run %s, found %s among %s",
                  name, runId, matches, agents)
            .hasSize(1);
      long pid = Long.parseLong(matches.get(0).split("\\s+", 2)[0]);
      exec("kill", "-9", String.valueOf(pid));
      return pid;
   }

   private static boolean hasProperty(String commandLine, String property, String value) {
      String flag = "-D" + property + "=" + value;
      for (String argument : commandLine.split("\\s+")) {
         if (flag.equals(argument)) {
            return true;
         }
      }
      return false;
   }

   private static List<String> exec(String... command) throws IOException, InterruptedException {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      List<String> lines;
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
         lines = reader.lines().collect(Collectors.toList());
      }
      process.waitFor();
      return lines;
   }
}
