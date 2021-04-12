package io.hyperfoil.clustering;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.clustering.messages.AgentReadyMessage;
import io.hyperfoil.clustering.messages.ErrorMessage;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.impl.SimulationRunner;
import io.hyperfoil.clustering.messages.PhaseChangeMessage;
import io.hyperfoil.clustering.messages.PhaseControlMessage;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.internal.Properties;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.impl.VertxInternal;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class AgentVerticle extends AbstractVerticle {
   private static Logger log = LogManager.getLogger(AgentVerticle.class);

   private String name;
   private String deploymentId;
   private String nodeId = "in-vm";
   private String runId;
   private EventBus eb;

   private SimulationRunner runner;
   private MessageConsumer<Object> controlFeedConsumer;
   private long statsTimerId = -1;
   private RequestStatsSender requestStatsSender;
   private CountDown statisticsCountDown;
   private SessionStatsSender sessionStatsSender;
   private ConnectionStatsSender connectionStatsSender;

   @Override
   public void start() {
      deploymentId = deploymentID();
      name = context.config().getString("name");
      if (name == null) {
         name = Properties.get(Properties.AGENT_NAME, null);
      }
      if (name == null) {
         try {
            name = InetAddress.getLocalHost().getHostName();
         } catch (UnknownHostException e) {
            log.debug("Cannot deduce name from host name", e);
            name = deploymentId;
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

      eb.consumer(deploymentId, message -> {
         try {
            AgentControlMessage controlMessage = (AgentControlMessage) message.body();
            if (controlMessage == null) {
               message.fail(1, "Could not decode message body. Does this Agent have the same version as the Controller?");
               return;
            }
            handleAgentControlMessage(message, controlMessage);
         } catch (Throwable t) {
            log.error("Processing of message failed", t);
            message.fail(1, t.getMessage());
         }
      });

      if (vertx.isClustered()) {
         if (vertx instanceof VertxInternal) {
            nodeId = ((VertxInternal) vertx).getClusterManager().getNodeId();
         }
      }
      vertx.setPeriodic(1000, timerId -> {
         eb.request(Feeds.DISCOVERY, new AgentHello(name, nodeId, deploymentId, runId), reply -> {
            log.trace("{} Pinging controller", deploymentId);
            if (reply.succeeded()) {
               log.info("{} Got reply from controller.", deploymentId);
               vertx.cancelTimer(timerId);
            } else {
               if (reply.cause() instanceof ReplyException) {
                  ReplyFailure replyFailure = ((ReplyException) reply.cause()).failureType();
                  if (replyFailure == ReplyFailure.RECIPIENT_FAILURE) {
                     log.error("{} Failed to register, already registered!", deploymentId);
                  } else {
                     log.info("{} Failed to register: {}", deploymentId, replyFailure);
                  }
               }
            }
         });
      });
   }

   private void handleAgentControlMessage(Message<Object> message, AgentControlMessage controlMessage) {
      switch (controlMessage.command()) {
         case INITIALIZE:
            log.info("Initializing agent");
            try {
               initBenchmark(controlMessage.benchmark(), controlMessage.agentId());
               message.reply("OK");
            } catch (Throwable e) {
               log.error("Failed to initialize agent", e);
               message.reply(e);
            }
            break;
         case STOP:
            // collect stats one last time before acknowledging termination
            log.info("Received agent reset");
            try {
               if (statsTimerId >= 0) {
                  vertx.cancelTimer(statsTimerId);
               }
               CountDown completion = new CountDown(result -> {
                  message.reply(result.succeeded() ? "OK" : result.cause());
                  if (vertx.isClustered()) {
                     // Give the message some time to be sent
                     vertx.setTimer(1000, id -> Hyperfoil.shutdownVertx(vertx));
                  } else {
                     vertx.undeploy(deploymentID());
                  }
               }, 1);
               if (runner != null) {
                  // TODO: why do we have to visit&send stats here?
                  runner.visitStatistics(requestStatsSender);
                  requestStatsSender.send(completion);
                  requestStatsSender.sendPhaseComplete(null, completion);
                  runner.shutdown();
               }
               if (controlFeedConsumer != null) {
                  controlFeedConsumer.unregister();
               }
               controlFeedConsumer = null;
               runner = null;
               requestStatsSender = null;
               if (statisticsCountDown != null) {
                  statisticsCountDown.setHandler(result -> completion.countDown());
                  statisticsCountDown.countDown();
               } else {
                  completion.countDown();
               }
            } catch (Throwable e) {
               log.error("Exception thrown when stopping the agent", e);
               message.reply(e);
            }
            break;
         case LIST_SESSIONS:
            log.debug("Listing sessions...");
            try {
               ArrayList<String> sessions = new ArrayList<>();
               boolean includeInactive = controlMessage.includeInactive();
               runner.visitSessions(s -> {
                  if (s.isActive() || includeInactive) {
                     sessions.add(s.toString());
                  }
               });
               message.reply(sessions);
            } catch (Throwable e) {
               log.error("Exception thrown when listing sessions", e);
               message.reply(e);
            }
            break;
         case LIST_CONNECTIONS:
            log.debug("Listing connections...");
            try {
               message.reply(runner.listConnections());
            } catch (Throwable e) {
               log.error("Exception thrown when listing connections", e);
               message.reply(e);
            }
            break;
      }
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

   private void initBenchmark(Benchmark benchmark, int agentId) {
      if (runner != null) {
         throw new IllegalStateException("Another simulation is running!");
      }

      Context context = vertx.getOrCreateContext();

      runner = new SimulationRunner(benchmark, runId, agentId,
            error -> eb.send(Feeds.RESPONSE, new ErrorMessage(deploymentId, runId, error, false)));
      controlFeedConsumer = listenOnControl();
      requestStatsSender = new RequestStatsSender(benchmark, eb, deploymentId, runId);
      statisticsCountDown = new CountDown(1);
      sessionStatsSender = new SessionStatsSender(eb, deploymentId, runId);
      connectionStatsSender = new ConnectionStatsSender(eb, deploymentId, runId);

      runner.setPhaseChangeHandler((phase, status, sessionLimitExceeded, error) -> {
         log.debug("{} changed phase {} to {}", deploymentId, phase, status);
         eb.send(Feeds.RESPONSE, new PhaseChangeMessage(deploymentId, runId, phase.name(), status, sessionLimitExceeded, error));
         if (status == PhaseInstance.Status.TERMINATED) {
            context.runOnContext(nil -> {
               if (runner != null) {
                  runner.visitStatistics(phase, requestStatsSender);
               }
               requestStatsSender.send(statisticsCountDown);
               requestStatsSender.sendPhaseComplete(phase, statisticsCountDown);
            });
         }
         return Util.COMPLETED_VOID_FUTURE;
      });
      runner.init();

      assert context.isEventLoopContext();
      statsTimerId = vertx.setPeriodic(benchmark.statisticsCollectionPeriod(), timerId -> {
         runner.visitStatistics(requestStatsSender);
         requestStatsSender.send(statisticsCountDown);
         runner.visitSessionPoolStats(sessionStatsSender);
         sessionStatsSender.send();
         runner.visitConnectionStats(connectionStatsSender);
         connectionStatsSender.send();
      });

      runner.openConnections(result -> {
         if (result.succeeded()) {
            eb.send(Feeds.RESPONSE, new AgentReadyMessage(deploymentID(), runId));
         } else {
            eb.send(Feeds.RESPONSE, new ErrorMessage(deploymentID(), runId, result.cause(), true));
         }
      });
   }
}
