package io.sailrocket.distributed;

import io.sailrocket.api.config.Simulation;
import io.sailrocket.core.impl.SimulationRunnerImpl;
import io.sailrocket.distributed.util.PhaseChangeMessage;
import io.sailrocket.distributed.util.PhaseControlMessage;
import io.sailrocket.distributed.util.ReportMessage;
import io.sailrocket.distributed.util.SimulationCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class AgentVerticle extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(AgentVerticle.class);

    private String address;
    private EventBus eb;
    private SimulationRunnerImpl runner;
    private long statsTimerId;

    @Override
    public void start() {
        eb = vertx.eventBus();
        address = deploymentID();
        eb.registerDefaultCodec(PhaseControlMessage.class, new PhaseControlMessage.Codec());
        eb.registerDefaultCodec(PhaseChangeMessage.class, new PhaseChangeMessage.Codec());
        eb.registerDefaultCodec(Simulation.class, new SimulationCodec());
        eb.registerDefaultCodec(ReportMessage.class, new ReportMessage.Codec());

        eb.consumer(address, message -> {
            Simulation simulation = (Simulation) message.body();
            if (!initSimulation(simulation)) {
                message.fail(1, "Agent already initialized");
            } else {
                message.reply("OK");
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
            eb.send(Feeds.DISCOVERY, address, reply -> {
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

    private boolean initSimulation(Simulation simulation) {
        if (runner != null) {
            return false;
        }
        runner = new SimulationRunnerImpl(simulation);
        runner.init((phase, status) -> eb.send(Feeds.RESPONSE, new PhaseChangeMessage(address, phase, status)));
        ReportSender reportSender = new ReportSender(simulation, eb, address);
        statsTimerId = vertx.setPeriodic(simulation.statisticsCollectionPeriod(), timerId -> {
            runner.visitSessions(reportSender);
            reportSender.send();
        });
        return true;
    }
}
