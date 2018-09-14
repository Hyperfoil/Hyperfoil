package io.sailrocket.distributed;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.Phase;
import io.sailrocket.api.Report;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.distributed.util.PhaseChangeMessage;
import io.sailrocket.distributed.util.PhaseControlMessage;
import io.sailrocket.distributed.util.ReportMessage;
import io.sailrocket.distributed.util.SimulationCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import org.HdrHistogram.Histogram;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AgentControllerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(AgentControllerVerticle.class);

    private EventBus eb;

    private Map<String, Benchmark> benchmarks = new HashMap<>();
    private Map<String, AgentInfo> agents = new HashMap<>();
    private Map<String, ControllerPhase> phases = new HashMap<>();

    private long startTime = Long.MIN_VALUE;
    private long terminateTime = Long.MIN_VALUE;
    private Benchmark benchmark;

    @Override
    public void start() {
        //create http api for controlling and monitoring agent
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.post("/upload").handler(this::handleUploadBenchmark);
        router.get("/start").handler(this::handleStartBenchmark);
        router.get("/agents").handler(this::handleAgentCount);
        router.get("/status").handler(this::handleStatus);
        router.get("/terminate").handler(this::handleTerminate);

        vertx.createHttpServer().requestHandler(router::accept).listen(8090);
        vertx.exceptionHandler(throwable -> log.error("Uncaught error: ", throwable));

        eb = vertx.eventBus();
        //TODO:: this is a code smell, not sure atm why i need to register the codec's multiple times
        eb.registerDefaultCodec(SimulationImpl.class, new SimulationCodec());
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
            for (Map.Entry<String, Report> entry : reportMessage.reports().entrySet()) {
               System.out.println(entry.getKey());
               entry.getValue().prettyPrint();
            }
        });
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
        ControllerPhase controllerPhase = phases.get(phase);
        switch (minStatus) {
            case RUNNING:
                controllerPhase.status(ControllerPhase.Status.RUNNING);
                break;
            case FINISHED:
                controllerPhase.status(ControllerPhase.Status.FINISHED);
                break;
            case TERMINATED:
                controllerPhase.status(ControllerPhase.Status.TERMINATED);
                break;
        }
    }

//        routingContext.response().putHeader("content-type", "application/json").end(formatJsonHistogram(collatedHistogram));

    private String formatJsonHistogram(Histogram histogram) {
        JsonObject jsonObject = new JsonObject();

        jsonObject.put("count", histogram.getTotalCount());
        jsonObject.put("min", TimeUnit.NANOSECONDS.toMillis(histogram.getMinValue()));
        jsonObject.put("max", TimeUnit.NANOSECONDS.toMillis(histogram.getMaxValue()));
        jsonObject.put("50%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(50)));
        jsonObject.put("90%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(90)));
        jsonObject.put("99%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99)));
        jsonObject.put("99.9%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99.9)));
        jsonObject.put("99.99%", TimeUnit.NANOSECONDS.toMillis(histogram.getValueAtPercentile(99.99)));

        return jsonObject.encode();
    }

    private void handleUploadBenchmark(RoutingContext routingContext) {
        String benchmark = routingContext.queryParam("benchmark").stream().findFirst().orElse("default");

        // TODO: allow this only for MIME:application/java-serialized-object
       byte[] bytes = routingContext.getBody().getBytes();
       try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            benchmarks.put(benchmark, (Benchmark) input.readObject());
            routingContext.response().setStatusCode(200).end();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            log.error("Failed to decode benchmark", e);
            routingContext.response().setStatusCode(400).end("Cannot decode benchmark.");
        }
    }

    private void handleAgentCount(RoutingContext routingContext) {
        //        return ((VertxImpl) vertx).getClusterManager().getNodes().size();
        routingContext.response().end(Integer.toString(agents.size()));
    }

    private void handleStartBenchmark(RoutingContext routingContext) {
        String benchmarkName = routingContext.request().getParam("benchmark");
        if (benchmarkName != null && benchmarks.containsKey(benchmarkName)) {
            benchmark = benchmarks.get(routingContext.request().getParam("benchmark"));

            for (AgentInfo agent : agents.values()) {
                if (agent.status != AgentInfo.Status.REGISTERED) {
                    log.error("Already initializing {}, status is {}!", agent.address, agent.status);
                } else {
                    agent.status = AgentInfo.Status.INITIALIZING;
                    eb.send(agent.address, benchmark.simulation(), reply -> {
                        if (reply.succeeded()) {
                            agent.status = AgentInfo.Status.INITIALIZED;
                            if (agents.values().stream().allMatch(a -> a.status == AgentInfo.Status.INITIALIZED)) {
                                assert startTime == Long.MIN_VALUE;
                                startTime = System.currentTimeMillis();
                                for (Phase phase : benchmark.simulation().phases()) {
                                    phases.put(phase.name(), new ControllerPhase(phase));
                                }
                                runSimulation();
                            }
                        } else {
                            agent.status = AgentInfo.Status.FAILED;
                            log.error("Agent {} failed to initialize", reply.cause(), agent.address);
                        }
                    });
                }
            }
            routingContext.response().setStatusCode(202).end("Initializing agents...");
        } else {
            //benchmark has not been defined yet
            String msg = "Benchmark not found";
            routingContext.response().setStatusCode(500).end(msg);
        }
    }

    private void handleStatus(RoutingContext routingContext) {
        JsonObject body = new JsonObject();
       SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
        if (benchmark != null) {
            body.put("benchmark", benchmark.name());
        }
        if (startTime > Long.MIN_VALUE) {
            body.put("started", simpleDateFormat.format(new Date(startTime)));
        }
        if (terminateTime > Long.MIN_VALUE) {
            body.put("terminated", simpleDateFormat.format(new Date(terminateTime)));
        }
        JsonArray jsonPhases = new JsonArray();
        body.put("phases", jsonPhases);
        for (ControllerPhase phase : phases.values()) {
            JsonObject jsonPhase = new JsonObject();
            jsonPhases.add(jsonPhase);
            jsonPhase.put("name", phase.definition().name);
            jsonPhase.put("status", phase.status());
            if (phase.absoluteStartTime() > Long.MIN_VALUE) {
                jsonPhase.put("started", simpleDateFormat.format(new Date(phase.absoluteStartTime())));
            }
        }
        JsonArray jsonAgents = new JsonArray();
        body.put("agents", jsonAgents);
        for (AgentInfo agent : agents.values()) {
            JsonObject jsonAgent = new JsonObject();
            jsonAgents.add(jsonAgent);
            jsonAgent.put("address", agent.address);
            jsonAgent.put("status", agent.status);
        }
        String status = body.encodePrettily();
        routingContext.response().end(status);
    }

    private void handleTerminate(RoutingContext routingContext) {
        // TODO
        routingContext.response().setStatusCode(202).end("TODO not implemented");
    }

    private void runSimulation() {
        long now = System.currentTimeMillis();
        for (ControllerPhase phase : phases.values()) {
            if (phase.status() == ControllerPhase.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.FINISH, null, phase.definition().name));
                phase.status(ControllerPhase.Status.FINISHING);
            }
            if (phase.status() == ControllerPhase.Status.FINISHED && phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
                eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.TERMINATE, null, phase.definition().name));
                phase.status(ControllerPhase.Status.TERMINATING);
            }
        }
        ControllerPhase[] availablePhases = getAvailablePhases();
        for (ControllerPhase phase : availablePhases) {
            eb.publish(Feeds.CONTROL, new PhaseControlMessage(PhaseControlMessage.Command.RUN, null, phase.definition().name));
            phase.absoluteStartTime(now);
            phase.status(ControllerPhase.Status.STARTING);
        }

        if (phases.values().stream().allMatch(phase -> phase.status() == ControllerPhase.Status.TERMINATED)) {
            terminateTime = now;
            // TODO reset agents, benchmark and so on...
            return;
        }

        long nextPhaseStart = phases.values().stream()
              .filter(phase -> phase.status() == ControllerPhase.Status.NOT_STARTED && phase.definition().startTime() >= 0)
              .mapToLong(phase -> this.startTime + phase.definition().startTime()).min().orElse(Long.MAX_VALUE);
        long nextPhaseFinish = phases.values().stream()
              .filter(phase -> phase.status() == ControllerPhase.Status.RUNNING)
              .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().duration()).min().orElse(Long.MAX_VALUE);
        long nextPhaseTerminate = phases.values().stream()
              .filter(phase -> (phase.status() == ControllerPhase.Status.RUNNING || phase.status() == ControllerPhase.Status.FINISHED) && phase.definition().maxDuration() >= 0)
              .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().maxDuration()).min().orElse(Long.MAX_VALUE);
        long delay = Math.min(Math.min(nextPhaseStart, nextPhaseFinish), nextPhaseTerminate) - System.currentTimeMillis();

        delay = Math.min(delay, 1000);
        log.debug("Wait {} ms", delay);
        vertx.setTimer(delay, timerId -> runSimulation());
    }

    private ControllerPhase[] getAvailablePhases() {
        return phases.values().stream().filter(phase -> phase.status() == ControllerPhase.Status.NOT_STARTED &&
              startTime + phase.definition().startTime() <= System.currentTimeMillis() &&
              phase.definition().startAfter().stream().allMatch(dep -> phases.get(dep).status().isFinished()) &&
              phase.definition().startAfterStrict().stream().allMatch(dep -> phases.get(dep).status() == ControllerPhase.Status.TERMINATED))
              .toArray(ControllerPhase[]::new);
    }

}