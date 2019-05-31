package io.hyperfoil.clustering;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.impl.SimulationRunnerImpl;
import io.hyperfoil.clustering.messages.PhaseChangeMessage;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class AgentVerticle extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(AgentVerticle.class);

    private String name;
    private String address;
    private String runId;
    private EventBus eb;

    private SimulationRunnerImpl runner;
    private MessageConsumer<Object> controlFeedConsumer;
    private long statsTimerId = -1;
    private ReportSender reportSender;
    private CountDown statisticsCountDown;
    private SessionStatsSender sessionStatsSender;

    @Override
    public void start() {
        address = deploymentID();
        name = context.config().getString("name");
        if (name == null) {
            name = Properties.get(Properties.AGENT_NAME, null);
        }
        if (name == null) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                log.debug("Cannot deduce name from host name", e);
                name = address;
            }
        }
        runId = context.config().getString("runId");
        if (runId == null) {
            runId = Properties.get(Properties.RUN_ID, null);
            if (runId == null) {
                throw new IllegalStateException("No run ID defined for this agent.");
            }
        }
        eb = vertx.eventBus();

        eb.consumer(address, message -> {
            AgentControlMessage controlMessage = (AgentControlMessage) message.body();
            switch (controlMessage.command()) {
                case INITIALIZE:
                    log.info("Initializing agent");
                    initBenchmark(controlMessage.benchmark(), result -> {
                        if (result.succeeded()) {
                            message.reply("OK");
                        } else {
                            message.fail(1, result.cause().getMessage());
                        }
                    });
                    break;
                case STOP:
                    // collect stats one last time before acknowledging termination
                    log.info("Received agent reset");
                    if (statsTimerId >= 0) {
                        vertx.cancelTimer(statsTimerId);
                    }
                    CountDown completion = new CountDown(result -> {
                        message.reply(result.succeeded() ? "OK" : result.cause());
                        if (vertx.isClustered()) {
                            // Give the message some time to be sent
                            vertx.setTimer(1000, id -> vertx.close());
                        } else {
                            vertx.undeploy(deploymentID());
                        }
                    }, 1);
                    if (runner != null) {
                        runner.visitStatistics(reportSender);
                        reportSender.send(completion);
                        runner.shutdown();
                    }
                    if (controlFeedConsumer != null) {
                        controlFeedConsumer.unregister();
                    }
                    controlFeedConsumer = null;
                    runner = null;
                    reportSender = null;
                    statisticsCountDown.setHandler(result -> completion.countDown());
                    statisticsCountDown.countDown();
                    break;
                case LIST_SESSIONS:
                    log.debug("Listing sessions...");
                    ArrayList<String> sessions = new ArrayList<>();
                    boolean includeInactive = controlMessage.includeInactive();
                    runner.visitSessions(s -> {
                        if (s.isActive() || includeInactive) {
                            sessions.add(s.toString());
                        }
                    });
                    message.reply(sessions);
                    break;
                case LIST_CONNECTIONS:
                    log.debug("Listing connections...");
                    message.reply(runner.listConnections());
                    break;
            }
        });

        vertx.setPeriodic(1000, timerId -> {
            eb.send(Feeds.DISCOVERY, new AgentHello(name, address, runId), reply -> {
                log.trace("{} Pinging controller", address);
                if (reply.succeeded()) {
                    log.info("{} Got reply from controller.", address);
                    vertx.cancelTimer(timerId);
                } else {
                    if (reply.cause() instanceof ReplyException) {
                        ReplyFailure replyFailure = ((ReplyException) reply.cause()).failureType();
                        if (replyFailure == ReplyFailure.RECIPIENT_FAILURE) {
                            log.error("{} Failed to register, already registered!", address);
                        } else {
                            log.info("{} Failed to register: {}", address, replyFailure);
                        }
                    }
                }
            });
        });
    }

    private MessageConsumer<Object> listenOnControl() {
        return eb.consumer(Feeds.CONTROL, message -> {
            PhaseControlMessage controlMessage = (PhaseControlMessage) message.body();
            switch (controlMessage.command()) {
                case RUN:
                    runner.startPhase(controlMessage.phase());
                    break;
                case FINISH:
                    runner.finishPhase(controlMessage.phase());
                    break;
                case TRY_TERMINATE:
                    runner.tryTerminatePhase(controlMessage.phase());
                    break;
                case TERMINATE:
                    runner.terminatePhase(controlMessage.phase());
                    break;
            }
        });
    }

    @Override
    public void stop() {
        if (runner != null) {
            runner.shutdown();
        }
    }

    private void initBenchmark(Benchmark benchmark, Handler<AsyncResult<Void>> handler) {
        if (runner != null) {
            log.error("Another simulation is running!");
            handler.handle(Future.failedFuture("Another simulation is running"));
            return;
        }
        runner = new SimulationRunnerImpl(benchmark, (phase, status, succesful, note) -> {
            log.debug("{} changed phase {} to {}", address, phase, status);
            eb.send(Feeds.RESPONSE, new PhaseChangeMessage(address, runId, phase.name(), status, succesful, note));
        });
        controlFeedConsumer = listenOnControl();
        reportSender = new ReportSender(benchmark, eb, address, runId);
        statisticsCountDown = new CountDown(1);
        sessionStatsSender = new SessionStatsSender(eb, address, runId);

        runner.init(result -> {
            if (result.succeeded()) {
                statsTimerId = vertx.setPeriodic(benchmark.statisticsCollectionPeriod(), timerId -> {
                    runner.visitStatistics(reportSender);
                    reportSender.send(statisticsCountDown);
                    runner.visitSessionPoolStats(sessionStatsSender);
                    sessionStatsSender.send();
                });
            }
            handler.handle(result);
        });
    }
}
