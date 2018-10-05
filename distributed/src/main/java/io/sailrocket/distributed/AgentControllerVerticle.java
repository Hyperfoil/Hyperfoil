package io.sailrocket.distributed;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.impl.statistics.StatisticsStore;
import io.sailrocket.distributed.util.PhaseChangeMessage;
import io.sailrocket.distributed.util.PhaseControlMessage;
import io.sailrocket.distributed.util.ReportMessage;
import io.sailrocket.distributed.util.SimulationCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentControllerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AgentControllerVerticle.class);

    private EventBus eb;
    private ControllerRestServer server;
    private AtomicInteger runIds = new AtomicInteger();
    private String runDir = "/tmp/SailRocket";

    Map<String, AgentInfo> agents = new HashMap<>();

    Run run;

    @Override
    public void start() {
        server = new ControllerRestServer(this);
        vertx.exceptionHandler(throwable -> log.error("Uncaught error: ", throwable));

        eb = vertx.eventBus();
        //TODO:: this is a code smell, not sure atm why i need to register the codec's multiple times
        eb.registerDefaultCodec(Simulation.class, new SimulationCodec());
        eb.registerDefaultCodec(PhaseChangeMessage.class, new PhaseChangeMessage.Codec());
        eb.registerDefaultCodec(PhaseControlMessage.class, new PhaseControlMessage.Codec());
        eb.registerDefaultCodec(ReportMessage.class, new ReportMessage.Codec());

        eb.consumer(Feeds.DISCOVERY, message -> {
            String address = (String) message.body();
            if (agents.containsKey(address) || agents.putIfAbsent(address, new AgentInfo(address)) != null) {
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
            String phase = phaseChange.phase();
            agent.phases.put(phase, phaseChange.status());
            tryProgressStatus(phase);
        });

        eb.consumer(Feeds.STATS, message -> {
            ReportMessage reportMessage = (ReportMessage) message.body();
            log.trace("Received stats from {}: {}/{} ({} requests)",
                  reportMessage.address, reportMessage.phase, reportMessage.sequence, reportMessage.statistics.requestCount);
            run.statisticsStore.record(reportMessage.address, reportMessage.phase, reportMessage.sequence, reportMessage.statistics);
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        server.stop(stopFuture);
    }

    private void tryProgressStatus(String phase) {
        PhaseInstance.Status minStatus = null;
        for (AgentInfo a : agents.values()) {
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
                    killCurrentRun();
                }
                controllerPhase.status(ControllerPhase.Status.TERMINATED);
                break;
        }
    }

    String startBenchmark(Benchmark benchmark) {
        this.run = new Run(String.format("%04X", runIds.getAndIncrement()), benchmark);

        for (AgentInfo agent : agents.values()) {
            if (agent.status != AgentInfo.Status.REGISTERED) {
                log.error("Already initializing {}, status is {}!", agent.address, agent.status);
            } else {
                agent.status = AgentInfo.Status.INITIALIZING;
                eb.send(agent.address, benchmark.simulation(), reply -> {
                    if (reply.succeeded()) {
                        agent.status = AgentInfo.Status.INITIALIZED;
                        if (agents.values().stream().allMatch(a -> a.status == AgentInfo.Status.INITIALIZED)) {
                            startSimulation();
                        }
                    } else {
                        agent.status = AgentInfo.Status.FAILED;
                        log.error("Agent {} failed to initialize", reply.cause(), agent.address);
                    }
                });
            }
        }
        return run.id;
    }

    private void startSimulation() {
        assert run.startTime == Long.MIN_VALUE;
        run.startTime = System.currentTimeMillis();
        for (Phase phase : run.benchmark.simulation().phases()) {
            run.phases.put(phase.name(), new ControllerPhase(phase));
        }
        run.statisticsStore = new StatisticsStore(run.benchmark, failure -> {
            Sequence sequence = failure.sla().sequence();
            System.out.println("Failed verify SLA(s) for " + sequence.phase() + "/" + sequence.name());
        });
        runSimulation();
    }

    private void runSimulation() {
        long now = System.currentTimeMillis();
        for (ControllerPhase phase : run.phases.values()) {
            if (phase.status() == ControllerPhase.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.FINISH, null, phase.definition().name));
                phase.status(ControllerPhase.Status.FINISHING);
            }
            if (phase.status() == ControllerPhase.Status.FINISHED && phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, null, phase.definition().name));
                phase.status(ControllerPhase.Status.TERMINATING);
            } else if (phase.definition().terminateAfterStrict().stream().map(run.phases::get).allMatch(p -> p.status() == ControllerPhase.Status.TERMINATED)) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TRY_TERMINATE, null, phase.definition().name));
            }
        }
        ControllerPhase[] availablePhases = run.getAvailablePhases();
        for (ControllerPhase phase : availablePhases) {
            eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.RUN, null, phase.definition().name));
            phase.absoluteStartTime(now);
            phase.status(ControllerPhase.Status.STARTING);
        }

        if (run.phases.values().stream().allMatch(phase -> phase.status() == ControllerPhase.Status.TERMINATED)) {
            stopSimulation();
            return;
        }

        long delay = run.nextTimestamp() - System.currentTimeMillis();

        delay = Math.min(delay, 1000);
        log.debug("Wait {} ms", delay);
        vertx.setTimer(delay, timerId -> runSimulation());
    }

    private void stopSimulation() {
        run.terminateTime = System.currentTimeMillis();
        vertx.executeBlocking(future -> {
            try {
                run.statisticsStore.persist(runDir + "/" + run.id + "/stats");
            } catch (IOException e) {
                log.error("Failed to persist statistics", e);
                future.fail(e);
            }
            future.complete();
        }, null);
        // TODO stop agents?
    }

    public Run run(String runId) {
        if (runId != null && run != null && runId.equals(run.id)) {
            return run;
        } else {
            return null;
        }
    }

    public Collection<Run> runs() {
        return run != null ? Collections.singleton(run) : Collections.emptyList();
    }

    public boolean kill(String runId) {
        if (runId != null && run != null && runId.equals(run.id)) {
            killCurrentRun();
            return true;
        } else {
            return false;
        }
    }

    private void killCurrentRun() {
        for (String phase : run.phases.keySet()) {
            eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, null, phase));
        }
    }
}