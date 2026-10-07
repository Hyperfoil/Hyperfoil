package io.hyperfoil.benchmark.clustering;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Kills an agent JVM with {@code SIGKILL} in the middle of a run and asserts that the controller notices and fails
 * the run promptly, rather than waiting for agents that will never report back.
 * <p>
 * This is the scenario the {@code infinispan.xml} comment carried over into {@code JGroupsClusterManager}'s
 * javadoc was about, but end to end: a real SSH-deployed agent process, the shipped {@code jgroups-tcp.xml} stack over real
 * TCP, and the whole controller on top. The in-JVM tests in {@code hyperfoil-clustering} cover the cluster
 * manager's own view handling over SHARED_LOOPBACK; what only this test covers is that
 * {@code JGroupsClusterManager.viewAccepted} actually reaches {@code ControllerVerticle.nodeLeft} and that the run
 * is then torn down instead of hanging.
 * <p>
 * The phase is deliberately far longer than the deadline we allow: if the crash were not detected the run could
 * only finish by running to completion, well after the assertion has already failed. A run that ends early and
 * blames the agent we killed can therefore only have got there through a failure-detection path.
 * <p>
 * Which path wins is a race and the test does not pin it down. Two of them notice independently: the SSH deployer
 * still holds the shell the agent was started from and sees it exit, and the cluster sees the view change and runs
 * {@code ControllerVerticle.nodeLeft}. In practice the deployer is first, because a closed shell is immediate
 * while failure detection has to go through FD_SOCK2 and VERIFY_SUSPECT2. Asserting on one particular message
 * would only make the test flaky the day the other one wins; what has to hold either way is that the run stops
 * quickly, that the error names the agent that died, and that the agent reads {@code FAILED} - the two paths
 * used to disagree about that last one, leaving it stuck in {@code STOPPING}.
 */
@Tag("io.hyperfoil.test.Benchmark")
public class AgentCrashTest extends BaseClusteredTest {
   private static final String SURVIVOR = "survivor";
   private static final String DOOMED = "doomed";
   /** Long enough that natural termination cannot be mistaken for crash handling. */
   private static final long PHASE_DURATION_MS = 120_000;
   /** FD_SOCK2 sees the closed socket almost at once; the rest is slack for a loaded CI machine. */
   private static final long DETECTION_DEADLINE_MS = 60_000;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      startController(ctx);
   }

   @Test
   public void shouldFailTheRunWhenAnAgentIsKilled() throws Exception {
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
            // The survivor is the control: it has to come all the way down cleanly, which is what says the run
            // was torn down properly rather than abandoned. The agent we killed has to read FAILED, and the
            // point of asserting the exact status is that both detection paths have to agree on it - whichever
            // of them wins the race marks the agent before the run is published as completed.
            assertThat(agent(info, SURVIVOR).status)
                  .withFailMessage("%s did not shut down cleanly, so the run was not torn down properly", SURVIVOR)
                  .isEqualTo("STOPPED");
            assertThat(agent(info, DOOMED).status)
                  .withFailMessage("%s was killed but the run reports it as %s", DOOMED, agent(info, DOOMED).status)
                  .isEqualTo("FAILED");
         } finally {
            // A run left behind would keep the controller from shutting down in tearDown.
            if (!run.get().completed) {
               run.kill();
            }
         }
      }
   }

   private void awaitAgentsRunning(Client.RunRef run) throws InterruptedException {
      long deadline = System.currentTimeMillis() + DETECTION_DEADLINE_MS;
      while (System.currentTimeMillis() < deadline) {
         Run info = run.get();
         // Both agents have to be past STARTING, otherwise we could kill a JVM that has not joined the cluster
         // yet - the controller would fail the run over the missing deployment rather than over the departure.
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

   /**
    * {@code SIGKILL}, not a graceful stop: the point is that survivors have to notice through failure detection
    * rather than being told. Matching on the run id as well as the agent name keeps a stray JVM left over from
    * another test out of the picture, and requiring exactly one match means an ambiguity fails loudly instead
    * of killing something arbitrary.
    */
   private static long killAgent(String runId, String name) throws IOException, InterruptedException {
      // The pattern deliberately omits the leading "-D": pgrep would take an argument starting with a dash for
      // one of its own options and quietly match nothing.
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

   /**
    * Compares whole arguments rather than substrings. {@code contains("...=doomed")} would also match an agent
    * called {@code doomed-2}, and demanding a trailing space instead would silently stop matching the day the
    * flag ends up last on the command line.
    */
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
