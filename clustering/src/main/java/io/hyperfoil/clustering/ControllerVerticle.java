package io.hyperfoil.clustering;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.RunHook;
import io.hyperfoil.api.deployment.AgentProperties;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.clustering.messages.SessionStatsMessage;
import io.hyperfoil.clustering.messages.StatsMessage;
import io.hyperfoil.core.hooks.ExecRunHook;
import io.hyperfoil.core.impl.statistics.StatisticsStore;
import io.hyperfoil.clustering.util.PersistenceUtil;
import io.hyperfoil.clustering.messages.PhaseChangeMessage;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.hyperfoil.clustering.messages.RequestStatsMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.infinispan.commons.api.BasicCacheContainer;

public class ControllerVerticle extends AbstractVerticle implements NodeListener {
   private static final Logger log = LoggerFactory.getLogger(ControllerVerticle.class);
   private static final Path ROOT_DIR = Properties.get(Properties.ROOT_DIR, Paths::get, Paths.get(System.getProperty("java.io.tmpdir"), "hyperfoil"));
   private static final Path RUN_DIR = Properties.get(Properties.RUN_DIR, Paths::get, ROOT_DIR.resolve("run"));
   private static final Path BENCHMARK_DIR = Properties.get(Properties.BENCHMARK_DIR, Paths::get, ROOT_DIR.resolve("benchmark"));
   private static final Path HOOKS_DIR = ROOT_DIR.resolve("hooks");
   private static final String DEPLOYER = Properties.get(AgentProperties.DEPLOYER, "ssh");
   private static final long DEPLOY_TIMEOUT = Properties.getLong(AgentProperties.DEPLOY_TIMEOUT, 15000);

   private EventBus eb;
   private ControllerServer server;
   private Deployer deployer;
   private AtomicInteger runIds = new AtomicInteger();
   private Map<String, Benchmark> benchmarks = new HashMap<>();
   private long timerId = -1;

   Map<String, Run> runs = new HashMap<>();

   @Override
   public void start(Future<Void> future) {
      log.info("Starting in directory {}...", RUN_DIR);
      server = new ControllerServer(this);
      vertx.exceptionHandler(throwable -> log.error("Uncaught error: ", throwable));
      if (Files.exists(RUN_DIR)) {
         try {
            Files.list(RUN_DIR).forEach(this::updateRuns);
         } catch (IOException e) {
            log.error("Could not list run dir contents", e);
         }
      }
      HOOKS_DIR.resolve("pre").toFile().mkdirs();
      HOOKS_DIR.resolve("post").toFile().mkdirs();

      eb = vertx.eventBus();

      eb.consumer(Feeds.DISCOVERY, message -> {
         AgentHello hello = (AgentHello) message.body();
         String runId = hello.runId();
         Run run = runs.get(runId);
         if (run == null) {
            log.error("Unknown run ID {}" + runId);
            message.fail(1, "Unknown run ID");
            return;
         }
         AgentInfo agentInfo = run.agents.stream().filter(a -> a.name.equals(hello.name())).findAny().orElse(null);
         if (agentInfo == null) {
            log.error("Unknown agent {} ({}/{})", hello.name(), hello.nodeId(), hello.deploymentId());
            message.fail(1, "Unknown agent");
            return;
         }
         if (agentInfo.status != AgentInfo.Status.STARTING) {
            log.info("Ignoring message, {} is not starting", agentInfo.name);
            message.reply("Ignoring");
            return;
         }
         log.debug("Registering agent {} ({}/{})", hello.name(), hello.nodeId(), hello.deploymentId());
         agentInfo.nodeId = hello.nodeId();
         agentInfo.deploymentId = hello.deploymentId();
         agentInfo.status = AgentInfo.Status.REGISTERED;
         message.reply("Registered");

         if (run.agents.stream().allMatch(a -> a.status != AgentInfo.Status.STARTING)) {
            handleAgentsStarted(run);
         } else {
            log.debug("Waiting for registration from agents {}",
                  run.agents.stream().filter(a -> a.status == AgentInfo.Status.STARTING).collect(Collectors.toList()));
         }
      });

      eb.consumer(Feeds.RESPONSE, message -> {
         PhaseChangeMessage phaseChange = (PhaseChangeMessage) message.body();
         Run run = runs.get(phaseChange.runId());
         if (run == null) {
            log.error("No run {}", phaseChange.runId());
            return;
         }
         AgentInfo agent = run.agents.stream().filter(a -> a.deploymentId.equals(phaseChange.senderId())).findAny().orElse(null);
         if (agent == null) {
            log.error("No agent {}", phaseChange.senderId());
            return;
         }
         String phase = phaseChange.phase();
         log.debug("Received phase change from {}: {} is {} (success={})",
               phaseChange.senderId(), phase, phaseChange.status(), phaseChange.getError());
         agent.phases.put(phase, phaseChange.status());
         if (phaseChange.getError() != null) {
            run.phases.get(phase).setFailed();
            run.errors.add(new Run.Error(agent, phaseChange.getError()));
         }
         tryProgressStatus(run, phase);
         runSimulation(run);
      });

      eb.consumer(Feeds.STATS, message -> {
         if (!(message.body() instanceof StatsMessage)) {
            log.error("Unknown message type: " + message.body());
            return;
         }
         StatsMessage statsMessage = (StatsMessage) message.body();
         Run run = runs.get(statsMessage.runId);
         if (run != null) {
            // Agents start sending stats before the server processes the confirmation for initialization
            if (run.statisticsStore != null) {
               if (statsMessage instanceof RequestStatsMessage) {
                  RequestStatsMessage requestStatsMessage = (RequestStatsMessage) statsMessage;
                  log.trace("Run {}: Received stats from {}: {}/{}/{} ({} requests)",
                        requestStatsMessage.runId, requestStatsMessage.address,
                        run.phase(requestStatsMessage.phaseId), requestStatsMessage.stepId, requestStatsMessage.metric,
                        requestStatsMessage.statistics.requestCount);
                  run.statisticsStore.record(requestStatsMessage.address, requestStatsMessage.phaseId, requestStatsMessage.stepId,
                        requestStatsMessage.metric, requestStatsMessage.statistics);
               } else if (statsMessage instanceof SessionStatsMessage) {
                  SessionStatsMessage sessionStatsMessage = (SessionStatsMessage) statsMessage;
                  log.trace("Run {}: Received session pool stats from {}", sessionStatsMessage.runId, sessionStatsMessage.address);
                  for (Map.Entry<String, SessionStatsMessage.MinMax> entry : sessionStatsMessage.sessionStats.entrySet()) {
                     run.statisticsStore.recordSessionStats(sessionStatsMessage.address,
                           sessionStatsMessage.timestamp, entry.getKey(), entry.getValue().min, entry.getValue().max);
                  }
               }
            }
         } else {
            log.error("Unknown run {}", statsMessage.runId);
         }
         message.reply("OK");
      });

      if (vertx.isClustered()) {
         for (Deployer.Factory deployerFactory : ServiceLoader.load(Deployer.Factory.class)) {
            log.debug("Found deployer {}", deployerFactory.name());
            if (DEPLOYER.equals(deployerFactory.name())) {
               deployer = deployerFactory.create();
               break;
            }
         }
         if (deployer == null) {
            throw new IllegalStateException("Hyperfoil is running in clustered mode but it couldn't load deployer '" + DEPLOYER + "'");
         }

         if (vertx instanceof VertxInternal) {
            ClusterManager clusterManager = ((VertxInternal) vertx).getClusterManager();
            clusterManager.nodeListener(this);
         }
      }

      if (!BENCHMARK_DIR.toFile().exists() && !BENCHMARK_DIR.toFile().mkdirs()) {
         log.error("Failed to create benchmark directory: {}", BENCHMARK_DIR);
      }
      loadBenchmarks(event -> future.complete());
   }

   @Override
   public void nodeAdded(String nodeID) {
   }

   @Override
   public void nodeLeft(String nodeID) {
      for (Run run : runs.values()) {
         if (run.terminateTime.isComplete()) {
            continue;
         }
         for (AgentInfo agent : run.agents) {
            if (Objects.equals(agent.nodeId, nodeID)) {
               agent.status = AgentInfo.Status.FAILED;
               run.errors.add(new Run.Error(agent, new BenchmarkExecutionException("Agent unexpectedly left the cluster.")));
               kill(run, result -> {
                  /* used version of checkstyle does not implement allowEmptyLambdas */
               });
               stopSimulation(run);
               break;
            }
         }
      }
   }

   private void updateRuns(Path runDir) {
      File file = runDir.toFile();
      if (!file.getName().matches("[0-9A-F][0-9A-F][0-9A-F][0-9A-F]")) {
         return;
      }
      String runId = file.getName();
      int id = Integer.parseInt(runId, 16);
      if (id >= runIds.get()) runIds.set(id + 1);
      Path infoFile = runDir.resolve("info.json");
      JsonObject info = new JsonObject();
      if (infoFile.toFile().exists() && infoFile.toFile().isFile()) {
         try {
            info = new JsonObject(new String(Files.readAllBytes(infoFile), StandardCharsets.UTF_8));
         } catch (IOException e) {
            log.error("Cannot read info for run {}", runId);
         }
      }
      Run run = new Run(runId, new Benchmark(info.getString("benchmark", "<unknown>"), null, Collections.emptyMap(), null, 0, null,
            Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), 0, Collections.emptyList(), Collections.emptyList()));
      run.startTime = info.getLong("startTime", 0L);
      run.terminateTime.complete(info.getLong("terminateTime", 0L));
      run.description = info.getString("description");
      runs.put(runId, run);
   }

   @Override
   public void stop(Future<Void> stopFuture) throws Exception {
      if (deployer != null) {
         deployer.close();
      }
      server.stop(stopFuture);
   }

   private void tryProgressStatus(Run run, String phase) {
      PhaseInstance.Status minStatus = null;
      for (AgentInfo a : run.agents) {
         PhaseInstance.Status status = a.phases.get(phase);
         if (status == null) {
            // The status is not defined on one of the nodes, so we can't progress it.
            return;
         } else if (minStatus == null || status.ordinal() < minStatus.ordinal()) {
            minStatus = status;
         }
      }
      ControllerPhase controllerPhase = run.phases.get(phase);
      switch (minStatus) {
         case RUNNING:
            controllerPhase.status(ControllerPhase.Status.RUNNING);
            break;
         case FINISHED:
            controllerPhase.status(ControllerPhase.Status.FINISHED);
            break;
         case TERMINATED:
            run.statisticsStore.completePhase(phase);
            if (!run.statisticsStore.validateSlas()) {
               log.info("SLA validation failed for {}", phase);
               controllerPhase.setFailed();
               run.errors.add(new Run.Error(null, new BenchmarkExecutionException("SLA validation failed for phase " + phase)));
            }
            controllerPhase.status(ControllerPhase.Status.TERMINATED);
            controllerPhase.absoluteCompletionTime(System.currentTimeMillis());
            break;
      }
      if (controllerPhase.isFailed()) {
         log.info("Phase {} failed, cancelling other phases...", controllerPhase.definition().name());
         for (ControllerPhase p : run.phases.values()) {
            if (p.status() == ControllerPhase.Status.NOT_STARTED) {
               p.status(ControllerPhase.Status.CANCELLED);
            }
         }
      }
   }

   Run createRun(Benchmark benchmark, String description) {
      String runId = String.format("%04X", runIds.getAndIncrement());
      Run run = new Run(runId, benchmark);
      run.description = description;
      run.statisticsStore = new StatisticsStore(run.benchmark, failure -> {
         log.warn("Failed verify SLA(s) for {}/{}: {}", failure.phase(), failure.metric(), failure.message());
      });
      runs.put(run.id, run);
      return run;
   }

   String startBenchmark(Run run) {
      Set<String> activeAgents = new HashSet<>();
      for (Run r : runs.values()) {
         if (!r.terminateTime.isComplete()) {
            for (AgentInfo agent : run.agents) {
               activeAgents.add(agent.name);
            }
         }
      }
      for (Agent agent : run.benchmark.agents()) {
         if (activeAgents.contains(agent.name)) {
            return "Agent " + agent + " is already used; try starting the benchmark later";
         }
      }

      if (run.benchmark.agents().length == 0) {
         if (vertx.isClustered()) {
            run.terminateTime.complete(System.currentTimeMillis());
            return "Server is started in clustered mode; benchmarks must define agents.";
         } else {
            run.agents.add(new AgentInfo("in-vm", 0));
            JsonObject config = new JsonObject().put("runId", run.id).put("name", "in-vm");
            vertx.deployVerticle(AgentVerticle.class, new DeploymentOptions().setConfig(config));
         }
      } else {
         if (!vertx.isClustered()) {
            return "Server is not started as clustered and does not accept benchmarks with agents defined.";
         }
         log.info("Starting agents for run {}", run.id);
         int agentCounter = 0;
         for (Agent agent : run.benchmark.agents()) {
            AgentInfo agentInfo = new AgentInfo(agent.name, agentCounter++);
            run.agents.add(agentInfo);
            log.debug("Starting agent {}", agent.name);
            vertx.executeBlocking(future -> agentInfo.deployedAgent = deployer.start(agent, run.id, exception -> {
               run.errors.add(new Run.Error(agentInfo, new BenchmarkExecutionException("Failed to deploy agent", exception)));
               log.error("Failed to deploy agent {}", exception, agent.name);
               vertx.runOnContext(nil -> stopSimulation(run));
            }), false, result -> {
               if (result.failed()) {
                  run.errors.add(new Run.Error(agentInfo, new BenchmarkExecutionException("Failed to start agent", result.cause())));
                  log.error("Failed to start agent {}", result.cause(), agent.name);
               }
            });
         }
      }

      run.deployTimerId = vertx.setTimer(DEPLOY_TIMEOUT, id -> {
         log.error("Deployment timed out.");
         stopSimulation(run);
      });

      return null;
   }

   private void handleAgentsStarted(Run run) {
      vertx.cancelTimer(run.deployTimerId);

      log.info("Starting benchmark {} - run {}", run.benchmark.name(), run.id);

      for (AgentInfo agent : run.agents) {
         if (agent.status != AgentInfo.Status.REGISTERED) {
            log.error("Already initializing {}, status is {}!", agent.deploymentId, agent.status);
         } else {
            agent.status = AgentInfo.Status.INITIALIZING;
            eb.send(agent.deploymentId, new AgentControlMessage(AgentControlMessage.Command.INITIALIZE, agent.id, run.benchmark), reply -> {
               if (reply.succeeded()) {
                  agent.status = AgentInfo.Status.INITIALIZED;
                  if (run.agents.stream().allMatch(a -> a.status == AgentInfo.Status.INITIALIZED)) {
                     startSimulation(run);
                  }
               } else {
                  log.error("Agent {}({}) failed to initialize", reply.cause(), agent.name, agent.deploymentId);
                  run.errors.add(new Run.Error(agent, reply.cause()));
                  stopSimulation(run);
               }
            });
         }
      }
   }

   private void startSimulation(Run run) {
      vertx.executeBlocking(future -> {
         // combine shared and benchmark-private hooks
         List<RunHook> hooks = loadHooks("pre");
         hooks.addAll(run.benchmark.preHooks());
         Collections.sort(hooks);

         for (RunHook hook : hooks) {
            StringBuilder sb = new StringBuilder();
            boolean success = hook.run(run.id, sb::append);
            run.hookResults.add(new Run.RunHookOutput(hook.name(), sb.toString()));
            if (!success) {
               future.fail("Execution of pre-hook " + hook.name() + " failed.");
               break;
            }
         }
         future.complete();
      }, result -> {
         if (result.succeeded()) {
            vertx.runOnContext(nil -> {
               assert run.startTime == Long.MIN_VALUE;
               run.startTime = System.currentTimeMillis();
               for (Phase phase : run.benchmark.phases()) {
                  run.phases.put(phase.name(), new ControllerPhase(phase));
               }
               runSimulation(run);
            });
         } else {
            log.error("Failed to start the simulation", result.cause());
            stopSimulation(run);
         }
      });
   }

   private void runSimulation(Run run) {
      if (timerId >= 0) {
         vertx.cancelTimer(timerId);
         timerId = -1;
      }
      long now = System.currentTimeMillis();
      for (ControllerPhase phase : run.phases.values()) {
         if (phase.status() == ControllerPhase.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
            eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.FINISH, phase.definition().name));
            phase.status(ControllerPhase.Status.FINISHING);
         }
         if (phase.status() == ControllerPhase.Status.FINISHED) {
            if (phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
               eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, phase.definition().name));
               phase.status(ControllerPhase.Status.TERMINATING);
            } else if (phase.definition().terminateAfterStrict().stream().map(run.phases::get).allMatch(p -> p.status().isTerminated())) {
               eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TRY_TERMINATE, phase.definition().name));
            }
         }
      }
      ControllerPhase[] availablePhases = run.getAvailablePhases();
      for (ControllerPhase phase : availablePhases) {
         eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.RUN, phase.definition().name));
         phase.absoluteStartTime(now);
         phase.status(ControllerPhase.Status.STARTING);
      }

      if (run.phases.values().stream().allMatch(phase -> phase.status().isTerminated())) {
         log.info("All phases are terminated.");
         stopSimulation(run);
         return;
      }

      long nextTimestamp = run.nextTimestamp();
      long delay = nextTimestamp - System.currentTimeMillis();

      delay = Math.min(delay, 1000);
      log.debug("Wait {} ms", delay);
      if (delay > 0) {
         if (timerId >= 0) {
            vertx.cancelTimer(timerId);
         }
         timerId = vertx.setTimer(delay, timerId -> runSimulation(run));
      } else {
         vertx.runOnContext(nil -> runSimulation(run));
      }
   }

   private void stopSimulation(Run run) {
      if (run.terminateTime.isComplete()) {
         log.warn("Run already completed.");
         return;
      }
      run.terminateTime.complete(System.currentTimeMillis());
      for (AgentInfo agent : run.agents) {
         if (agent.deploymentId == null) {
            assert agent.status == AgentInfo.Status.STARTING;
            if (agent.deployedAgent != null) {
               agent.deployedAgent.stop();
            }
            continue;
         }
         eb.send(agent.deploymentId, new AgentControlMessage(AgentControlMessage.Command.STOP, agent.id, null), reply -> {
            if (reply.succeeded()) {
               agent.status = AgentInfo.Status.STOPPED;
               checkAgentsStopped(run);
            } else {
               agent.status = AgentInfo.Status.FAILED;
               log.error("Agent {} failed to stop", reply.cause(), agent.deploymentId);
            }
            if (agent.deployedAgent != null) {
               agent.deployedAgent.stop();
            }
         });
      }
      checkAgentsStopped(run);
   }

   private void checkAgentsStopped(Run run) {
      if (run.agents.stream().allMatch(a -> a.status != AgentInfo.Status.INITIALIZED)) {
         persistRun(run);
         log.info("Run {} completed", run.id);
      }
   }

   private void persistRun(Run run) {
      vertx.executeBlocking(future -> {
         Path runDir = RUN_DIR.resolve(run.id);
         runDir.toFile().mkdirs();
         try {
            run.statisticsStore.persist(runDir.resolve("stats"));
         } catch (IOException e) {
            log.error("Failed to persist statistics", e);
            future.fail(e);
         }

         // combine shared and benchmark-private hooks
         List<RunHook> hooks = loadHooks("post");
         hooks.addAll(run.benchmark.postHooks());
         Collections.sort(hooks);

         for (RunHook hook : hooks) {
            StringBuilder sb = new StringBuilder();
            boolean success = hook.run(run.id, sb::append);
            run.hookResults.add(new Run.RunHookOutput(hook.name(), sb.toString()));
            if (!success) {
               log.error("Execution of post-hook " + hook.name() + " failed.");
               // stop executing further hooks but persist info
               break;
            }
         }


         JsonObject info = new JsonObject()
               .put("id", run.id)
               .put("benchmark", run.benchmark.name())
               .put("startTime", run.startTime)
               .put("terminateTime", run.terminateTime.result())
               .put("description", run.description)
               .put("errors", new JsonArray(run.errors.stream()
                     .map(e -> new JsonObject().put("agent", e.agent.name).put("msg", e.error.getMessage()))
                     .collect(Collectors.toList())))
               .put("hooks", new JsonArray(run.hookResults.stream()
                     .map(r -> new JsonObject().put("name", r.name).put("output", r.output))
                     .collect(Collectors.toList())));

         try (FileOutputStream stream = new FileOutputStream("all.json")) {
            JsonFactory jfactory = new JsonFactory();
            jfactory.setCodec(new ObjectMapper());
            JsonGenerator jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
            jGenerator.setCodec(new ObjectMapper());
            jGenerator.writeStartObject();

            jGenerator.writeObjectField("info", info);
            run.statisticsStore.writeJson(jGenerator, false);
            jGenerator.writeEndObject();
            jGenerator.close();
         } catch (FileNotFoundException e) {
            log.error("Cannot write all.json file", e);
            future.fail(e);
         } catch (IOException e) {
            log.error("Cannot write all.json file", e);
            future.fail(e);
         }


         try {
            Files.write(runDir.resolve("info.json"), info.encodePrettily().getBytes(StandardCharsets.UTF_8));
         } catch (IOException e) {
            log.error("Cannot write info file", e);
            future.fail(e);
         }
         PersistenceUtil.store(run.benchmark, runDir);
         if (!future.isComplete()) {
            future.complete();
         }
      }, null);
   }

   public Run run(String runId) {
      return runs.get(runId);
   }

   public Collection<Run> runs() {
      return runs.values();
   }

   public void kill(Run run, Handler<AsyncResult<Void>> handler) {
      try {
         for (Map.Entry<String, ControllerPhase> entry : run.phases.entrySet()) {
            ControllerPhase.Status status = entry.getValue().status();
            if (!status.isTerminated()) {
               if (status == ControllerPhase.Status.NOT_STARTED) {
                  entry.getValue().status(ControllerPhase.Status.CANCELLED);
               } else {
                  entry.getValue().status(ControllerPhase.Status.TERMINATING);
                  eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, entry.getKey()));
               }
            }
         }
         run.terminateTime.setHandler(result -> handler.handle(result.mapEmpty()));
      } catch (Throwable e) {
         handler.handle(Future.failedFuture(e));
      }
   }

   public void addBenchmark(Benchmark benchmark, Handler<AsyncResult<Void>> handler) {
      benchmarks.put(benchmark.name(), benchmark);
      vertx.executeBlocking(future -> {
         PersistenceUtil.store(benchmark, BENCHMARK_DIR);
         future.complete();
      }, handler);
   }

   public Collection<String> getBenchmarks() {
      return benchmarks.keySet();
   }

   public Benchmark getBenchmark(String name) {
      return benchmarks.get(name);
   }

   private void loadBenchmarks(Handler<AsyncResult<Void>> handler) {
      vertx.executeBlocking(future -> {
         try {
            Files.list(BENCHMARK_DIR).forEach(file -> {
               Benchmark benchmark = PersistenceUtil.load(file);
               if (benchmark != null) {
                  benchmarks.put(benchmark.name(), benchmark);
               }
            });
         } catch (IOException e) {
            log.error("Failed to list benchmark dir {}", e, BENCHMARK_DIR);
         }
         future.complete();
      }, handler);
   }

   private List<RunHook> loadHooks(String subdir) {
      try {
         File preHookDir = HOOKS_DIR.resolve(subdir).toFile();
         if (preHookDir.exists() && preHookDir.isDirectory()) {
            return Files.list(preHookDir.toPath()).map(path -> {
               File file = path.toFile();
               return new ExecRunHook(file.getName(), file.getAbsolutePath());
            }).collect(Collectors.toList());
         }
      } catch (IOException e) {
         log.error("Failed to list hooks.", e);
      }
      return Collections.emptyList();
   }


   public void listSessions(Run run, boolean includeInactive, BiConsumer<AgentInfo, String> sessionStateHandler, Handler<AsyncResult<Void>> completionHandler) {
      invokeOnAgents(run, AgentControlMessage.Command.LIST_SESSIONS, includeInactive, completionHandler, (agent, result) -> {
         @SuppressWarnings("unchecked")
         List<String> sessions = (List<String>) result.result().body();
         for (String state : sessions) {
            sessionStateHandler.accept(agent, state);
         }
      });
   }

   public void listConnections(Run run, BiConsumer<AgentInfo, String> connectionHandler, Handler<AsyncResult<Void>> completionHandler) {
      invokeOnAgents(run, AgentControlMessage.Command.LIST_CONNECTIONS, null, completionHandler, (agent, result) -> {
         @SuppressWarnings("unchecked")
         List<String> connections = (List<String>) result.result().body();
         for (String state : connections) {
            connectionHandler.accept(agent, state);
         }
      });
   }

   private void invokeOnAgents(Run run, AgentControlMessage.Command command, Object param, Handler<AsyncResult<Void>> completionHandler, BiConsumer<AgentInfo, AsyncResult<Message<Object>>> handler) {
      AtomicInteger agentCounter = new AtomicInteger(1);
      for (AgentInfo agent : run.agents) {
         agentCounter.incrementAndGet();
         eb.send(agent.deploymentId, new AgentControlMessage(command, agent.id, param), result -> {
            if (result.failed()) {
               log.error("Failed to retrieve sessions", result.cause());
               completionHandler.handle(Future.failedFuture(result.cause()));
            } else {
               handler.accept(agent, result);
               if (agentCounter.decrementAndGet() == 0) {
                  completionHandler.handle(Future.succeededFuture());
               }
            }
         });
      }
      if (agentCounter.decrementAndGet() == 0) {
         completionHandler.handle(Future.succeededFuture());
      }
   }

   public void downloadAgentLog(DeployedAgent deployedAgent, long offset, File tempFile, Handler<AsyncResult<Void>> handler) {
      vertx.executeBlocking(future -> {
         deployer.downloadAgentLog(deployedAgent, offset, tempFile.toString(), handler);
      }, result -> {
         if (result.failed()) {
            handler.handle(Future.failedFuture(result.cause()));
         }
      });
   }

   public Benchmark ensureBenchmark(Run run) {
      if (run.benchmark.source() == null) {
         File serializedSource = RUN_DIR.resolve(run.id).resolve(run.benchmark.name() + ".serialized").toFile();
         if (serializedSource.exists() && serializedSource.isFile()) {
            run.benchmark = PersistenceUtil.load(serializedSource.toPath());
            return run.benchmark;
         }
         File yamlSource = RUN_DIR.resolve(run.id).resolve(run.benchmark.name() + ".yaml").toFile();
         if (yamlSource.exists() && yamlSource.isFile()) {
            run.benchmark = PersistenceUtil.load(yamlSource.toPath());
            return run.benchmark;
         }
         log.warn("Cannot find benchmark source for run " + run.id + ", benchmark " + run.benchmark.name());
      }
      return run.benchmark;
   }

   public void shutdown() {
      BasicCacheContainer cacheManager = ((InfinispanClusterManager) ((VertxInternal) vertx).getClusterManager()).getCacheContainer();
      vertx.close(ar -> cacheManager.stop());
   }
}