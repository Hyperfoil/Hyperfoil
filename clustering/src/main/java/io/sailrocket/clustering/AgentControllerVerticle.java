package io.sailrocket.clustering;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.config.Host;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.session.PhaseInstance;
import io.sailrocket.clustering.util.AgentControlMessage;
import io.sailrocket.clustering.util.AgentHello;
import io.sailrocket.core.impl.statistics.StatisticsStore;
import io.sailrocket.clustering.util.PersistenceUtil;
import io.sailrocket.clustering.util.PhaseChangeMessage;
import io.sailrocket.clustering.util.PhaseControlMessage;
import io.sailrocket.clustering.util.ReportMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class AgentControllerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AgentControllerVerticle.class);
    private static final Path ROOT_DIR = getConfiguredPath(Properties.ROOT_DIR, Paths.get(System.getProperty("java.io.tmpdir"), "sailrocket"));
    private static final Path RUN_DIR = getConfiguredPath(Properties.RUN_DIR, ROOT_DIR.resolve("run"));
    private static final Path BENCHMARK_DIR = getConfiguredPath(Properties.BENCHMARK_DIR, ROOT_DIR.resolve("benchmark"));

    private EventBus eb;
    private ControllerRestServer server;
    private AtomicInteger runIds = new AtomicInteger();
    private Map<String, Benchmark> benchmarks = new HashMap<>();
    private long timerId = -1;

    Map<String, AgentInfo> agents = new HashMap<>();
    Map<String, Run> runs = new HashMap<>();

    private static Path getConfiguredPath(String property, Path def) {
        String path = System.getProperty(property);
        if (path != null) {
            return Paths.get(path);
        }
        path = System.getenv(property.replaceAll("\\.", "_").toUpperCase());
        if (path != null) {
            return Paths.get(path);
        }
        return def;
    }

    @Override
    public void start(Future<Void> future) {
        log.info("Starting in directory {}...", RUN_DIR);
        server = new ControllerRestServer(this);
        vertx.exceptionHandler(throwable -> log.error("Uncaught error: ", throwable));
        if (Files.exists(RUN_DIR)) {
            try {
                Files.list(RUN_DIR).forEach(this::updateRuns);
            } catch (IOException e) {
                log.error("Could not list run dir contents", e);
            }
        }

        eb = vertx.eventBus();

        eb.consumer(Feeds.DISCOVERY, message -> {
            AgentHello hello = (AgentHello) message.body();
            String address = hello.address();
            if (agents.containsKey(address) || agents.putIfAbsent(address, new AgentInfo(hello.name(), address)) != null) {
                message.fail(1, "Agent already present");
            } else {
                message.reply("Registered");
            }
        });

        eb.consumer(Feeds.RESPONSE, message -> {
            PhaseChangeMessage phaseChange = (PhaseChangeMessage) message.body();
            AgentInfo agent = agents.get(phaseChange.senderId());
            if (agent == null) {
               log.error("No agent {}", phaseChange.senderId());
               return;
            }
            Run run = runs.get(phaseChange.runId());
            if (run == null) {
                log.error("No run {}", phaseChange.runId());
                return;
            }
            String phase = phaseChange.phase();
            log.debug("Received phase change from {}: {} is {} (success={})",
                  phaseChange.senderId(), phase, phaseChange.status(), phaseChange.isSuccessful());
            agent.phases.put(phase, phaseChange.status());
            if (!phaseChange.isSuccessful()) {
                run.phases.get(phase).setFailed();
            }
            tryProgressStatus(run, phase);
            runSimulation(run);
        });

        eb.consumer(Feeds.STATS, message -> {
            ReportMessage reportMessage = (ReportMessage) message.body();
            log.trace("Run {}: Received stats from {}: {}/{} ({} requests)", reportMessage.runId,
                  reportMessage.address, reportMessage.phase, reportMessage.sequence, reportMessage.statistics.requestCount);
            Run run = runs.get(reportMessage.runId);
            if (run != null) {
                // Agents start sending stats before the server processes the confirmation for initialization
                if (run.statisticsStore != null) {
                    run.statisticsStore.record(reportMessage.address, reportMessage.phase, reportMessage.sequence, reportMessage.statistics);
                }
            } else {
                log.error("Unknown run {}", reportMessage.runId);
            }
        });

        BENCHMARK_DIR.toFile().mkdirs();
        loadBenchmarks(event -> future.complete());
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
        Run run = new Run(runId, new Benchmark(info.getString("benchmark", "<unknown>"), null, null, null, null), Collections.emptyList());
        run.startTime = info.getLong("startTime", 0L);
        run.terminateTime = info.getLong("terminateTime", 0L);
        runs.put(runId, run);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
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
                if (!run.statisticsStore.validateSlas(phase)) {
                    controllerPhase.setFailed();
                }
                controllerPhase.status(ControllerPhase.Status.TERMINATED);
                controllerPhase.absoluteTerminateTime(System.currentTimeMillis());
                break;
        }
        cancelDependentPhases(run, controllerPhase);
    }

    private void cancelDependentPhases(Run run, ControllerPhase controllerPhase) {
        if (controllerPhase.isFailed()) {
            ArrayDeque<ControllerPhase> queue = new ArrayDeque<>(run.phases.values());
            boolean changed = true;
            OUTER: while (changed && !queue.isEmpty()) {
                ControllerPhase p = queue.pollFirst();
                if (p.status() != ControllerPhase.Status.NOT_STARTED) {
                    continue;
                }
                for (String dep : p.definition().startAfter) {
                    ControllerPhase depPhase = run.phases.get(dep);
                    if (depPhase.isFailed() || depPhase.status() == ControllerPhase.Status.CANCELLED) {
                        changed = true;
                        p.status(ControllerPhase.Status.CANCELLED);
                        continue OUTER;
                    }
                }
                for (String dep : p.definition().startAfterStrict) {
                    ControllerPhase depPhase = run.phases.get(dep);
                    if (depPhase.isFailed() || depPhase.status() == ControllerPhase.Status.CANCELLED) {
                        changed = true;
                        p.status(ControllerPhase.Status.CANCELLED);
                        continue OUTER;
                    }
                }
                queue.addLast(p);
                // let's ignore terminateAfterStrict as this might be already started
            }
        }
    }

    String startBenchmark(Benchmark benchmark) {
        List<AgentInfo> runAgents = new ArrayList<>();
        if (benchmark.agents().length == 0) {
            if (agents.isEmpty()) {
                return null;
            }
            runAgents.add(agents.values().iterator().next());
        } else {
            for (Host host : benchmark.agents()) {
                Optional<AgentInfo> opt = agents.values().stream().filter(a -> Objects.equals(a.name, host.name)).findFirst();
                if (opt.isPresent()) {
                    runAgents.add(opt.get());
                } else {
                    log.error("Agent {} ({}:{}) not registered", host.name, host.hostname, host.username);
                    return null;
                }
            }
        }

        Run run = new Run(String.format("%04X", runIds.getAndIncrement()), benchmark, runAgents);
        runs.put(run.id, run);
        log.info("Starting benchmark {} - run {}", run.benchmark.name(), run.id);

        for (AgentInfo agent : run.agents) {
            if (agent.status != AgentInfo.Status.REGISTERED) {
                log.error("Already initializing {}, status is {}!", agent.address, agent.status);
            } else {
                agent.status = AgentInfo.Status.INITIALIZING;
                eb.send(agent.address, new AgentControlMessage(AgentControlMessage.Command.INITIALIZE, run.id, benchmark.simulation()), reply -> {
                    if (reply.succeeded()) {
                        agent.status = AgentInfo.Status.INITIALIZED;
                        if (run.agents.stream().allMatch(a -> a.status == AgentInfo.Status.INITIALIZED)) {
                            startSimulation(run);
                        }
                    } else {
                        log.error("Agent {} failed to initialize", reply.cause(), agent.address);
                        stopSimulation(run);
                    }
                });
            }
        }
        return run.id;
    }

    private void startSimulation(Run run) {
        assert run.startTime == Long.MIN_VALUE;
        run.startTime = System.currentTimeMillis();
        for (Phase phase : run.benchmark.simulation().phases()) {
            run.phases.put(phase.name(), new ControllerPhase(phase));
        }
        run.statisticsStore = new StatisticsStore(run.benchmark, failure -> {
            Sequence sequence = failure.sla().sequence();
            log.warn("Failed verify SLA(s) for {}/{}", sequence.phase(), sequence.name());
        });
        runSimulation(run);
    }

    private void runSimulation(Run run) {
        if (timerId >= 0) {
            vertx.cancelTimer(timerId);
            timerId = -1;
        }
        long now = System.currentTimeMillis();
        for (ControllerPhase phase : run.phases.values()) {
            if (phase.status() == ControllerPhase.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.FINISH, null, phase.definition().name));
                phase.status(ControllerPhase.Status.FINISHING);
            }
            if (phase.status() == ControllerPhase.Status.FINISHED) {
                if (phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
                    eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, null, phase.definition().name));
                    phase.status(ControllerPhase.Status.TERMINATING);
                } else if (phase.definition().terminateAfterStrict().stream().map(run.phases::get).allMatch(p -> p.status().isTerminated())) {
                    eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TRY_TERMINATE, null, phase.definition().name));
                }
            }
        }
        ControllerPhase[] availablePhases = run.getAvailablePhases();
        for (ControllerPhase phase : availablePhases) {
            eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.RUN, null, phase.definition().name));
            phase.absoluteStartTime(now);
            phase.status(ControllerPhase.Status.STARTING);
        }

        if (run.phases.values().stream().allMatch(phase -> phase.status().isTerminated())) {
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
        run.terminateTime = System.currentTimeMillis();
        for (AgentInfo agent : run.agents) {
            eb.send(agent.address, new AgentControlMessage(AgentControlMessage.Command.RESET, run.id, null), reply -> {
                if (reply.succeeded()) {
                    agent.status = AgentInfo.Status.REGISTERED;
                    if (run.agents.stream().allMatch(a -> a.status != AgentInfo.Status.INITIALIZED)) {
                        run.agents.clear();
                        persistRun(run);
                        log.info("Run {} completed", run.id);
                    }
                } else {
                    agent.status = AgentInfo.Status.FAILED;
                    log.error("Agent {} failed to stop", reply.cause(), agent.address);
                }
            });
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
            JsonObject info = new JsonObject()
                  .put("id", run.id)
                  .put("benchmark", run.benchmark.name())
                  .put("startTime", run.startTime)
                  .put("terminateTime", run.terminateTime);
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

    public void kill(Run run) {
        for (Map.Entry<String, ControllerPhase> entry : run.phases.entrySet()) {
            ControllerPhase.Status status = entry.getValue().status();
            if (!status.isTerminated()) {
                if (status == ControllerPhase.Status.NOT_STARTED) {
                    entry.getValue().status(ControllerPhase.Status.CANCELLED);
                } else {
                    entry.getValue().status(ControllerPhase.Status.TERMINATING);
                    eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, null, entry.getKey()));
                }
            }
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
                log.error(e, "Failed to list benchmark dir {}", BENCHMARK_DIR);
            }
            future.complete();
        }, handler);
    }

   public void listSessions(Run run, boolean includeInactive, BiConsumer<AgentInfo, String> sessionStateHandler, Handler<AsyncResult<Void>> completionHandler) {
      invokeOnAgents(run, AgentControlMessage.Command.LIST_SESSIONS, includeInactive, completionHandler, (agent, result) -> {
         for (String state : (List<String>) result.result().body()) {
            sessionStateHandler.accept(agent, state);
         }
      });
   }

   public void listConnections(Run run, BiConsumer<AgentInfo, String> connectionHandler, Handler<AsyncResult<Void>> completionHandler) {
     invokeOnAgents(run, AgentControlMessage.Command.LIST_CONNECTIONS, null, completionHandler, (agent, result) -> {
        for (String state : (List<String>) result.result().body()) {
           connectionHandler.accept(agent, state);
        }
     });
   }

   private void invokeOnAgents(Run run, AgentControlMessage.Command command, Object param, Handler<AsyncResult<Void>> completionHandler, BiConsumer<AgentInfo, AsyncResult<Message<Object>>> handler) {
      AtomicInteger agentCounter = new AtomicInteger(1);
      for (AgentInfo agent : run.agents) {
         agentCounter.incrementAndGet();
         eb.send(agent.address, new AgentControlMessage(command, run.id, param), result -> {
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
}