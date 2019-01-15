package io.hyperfoil.clustering;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.clustering.util.AgentControlMessage;
import io.hyperfoil.clustering.util.AgentHello;
import io.hyperfoil.core.impl.SimulationRunnerImpl;
import io.hyperfoil.clustering.util.PhaseChangeMessage;
import io.hyperfoil.clustering.util.PhaseControlMessage;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class AgentVerticle extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(AgentVerticle.class);

    private String name;
    private String address;
    private EventBus eb;

    private SimulationRunnerImpl runner;
    private long statsTimerId = -1;
    private ReportSender reportSender;

    @Override
    public void start() {
        address = deploymentID();
        name = context.config().getString("name");
        if (name == null) {
            name = System.getProperty(Properties.AGENT_NAME);
        }
        if (name == null) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                log.debug("Cannot deduce name from host name", e);
                name = address;
            }
        }
        eb = vertx.eventBus();

        eb.consumer(address, message -> {
            AgentControlMessage controlMessage = (AgentControlMessage) message.body();
            switch (controlMessage.command()) {
                case INITIALIZE:
                    initSimulation(controlMessage.runId(), controlMessage.simulation(), result -> {
                        if (result.succeeded()) {
                            message.reply("OK");
                        } else {
                            message.fail(1, result.cause().getMessage());
                        }
                    });
                    break;
                case RESET:
                    // collect stats one last time before acknowledging termination
                    if (statsTimerId >= 0) {
                        vertx.cancelTimer(statsTimerId);
                    }
                    if (runner != null) {
                        runner.visitStatistics(reportSender);
                        reportSender.send();
                        runner.shutdown();
                    }
                    runner = null;
                    reportSender = null;
                    // TODO: this does not guarantee in-order delivery
                    message.reply("OK");
                    break;
                case LIST_SESSIONS:
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
                    message.reply(runner.listConnections());
                    break;
            }
        });

        eb.consumer(Feeds.CONTROL, message -> {
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

        vertx.setPeriodic(1000, timerId -> {
            eb.send(Feeds.DISCOVERY, new AgentHello(name, address), reply -> {
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

    @Override
    public void stop() {
        if (runner != null) {
            runner.shutdown();
        }
    }

    private void initSimulation(String runId, Simulation simulation, Handler<AsyncResult<Void>> handler) {
        if (runner != null) {
            handler.handle(Future.failedFuture("Another simulation is running"));
            return;
        }
        runner = new SimulationRunnerImpl(simulation);
        reportSender = new ReportSender(simulation, eb, address, runId);

        runner.init((phase, status, succesful) -> {
            log.debug("{} changed phase {} to {}", address, phase, status);
            eb.send(Feeds.RESPONSE, new PhaseChangeMessage(address, runId, phase, status, succesful));
        }, result -> {
            if (result.succeeded()) {
                statsTimerId = vertx.setPeriodic(simulation.statisticsCollectionPeriod(), timerId -> {
                    runner.visitStatistics(reportSender);
                    reportSender.send();
                });
            }
            handler.handle(result);
        });
    }
}
