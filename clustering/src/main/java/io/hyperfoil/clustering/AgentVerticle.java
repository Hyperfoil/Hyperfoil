package io.hyperfoil.clustering;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.clustering.messages.AgentControlMessage;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.clustering.messages.AgentReadyMessage;
import io.hyperfoil.clustering.messages.ErrorMessage;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.impl.SimulationRunnerImpl;
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class AgentVerticle extends AbstractVerticle {
   private static Logger log = LoggerFactory.getLogger(AgentVerticle.class);

   private String name;
   private String deploymentId;
   private String nodeId = "in-vm";
   private String runId;
   private EventBus eb;

   private SimulationRunnerImpl runner;
   private MessageConsumer<Object> controlFeedConsumer;
   private long statsTimerId = -1;
   private RequestStatsSender requestStatsSender;
   private CountDown statisticsCountDown;
   private SessionStatsSender sessionStatsSender;

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
            nodeId = ((VertxInternal) vertx).getClusterManager().getNodeID();
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
               message.fail(1, e.getMessage());
            }
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
               runner.visitStatistics(requestStatsSender);
               requestStatsSender.send(true, completion);
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

      runner = new SimulationRunnerImpl(benchmark, agentId);
      controlFeedConsumer = listenOnControl();
      requestStatsSender = new RequestStatsSender(benchmark, eb, deploymentId, runId);
      statisticsCountDown = new CountDown(1);
      sessionStatsSender = new SessionStatsSender(eb, deploymentId, runId);

      runner.setPhaseChangeHandler((phase, status, sessionLimitExceeded, error) -> {
         log.debug("{} changed phase {} to {}", deploymentId, phase, status);
         eb.send(Feeds.RESPONSE, new PhaseChangeMessage(deploymentId, runId, phase.name(), status, sessionLimitExceeded, error));
         if (status == PhaseInstance.Status.TERMINATED) {
            context.runOnContext(nil -> {
               runner.visitStatistics(phase, requestStatsSender);
               requestStatsSender.send(true, statisticsCountDown);
            });
         }
         return Util.COMPLETED_VOID_FUTURE;
      });
      runner.setErrorHandler(error -> {
         eb.send(Feeds.RESPONSE, new ErrorMessage(deploymentId, runId, error, false));
      });
      runner.init();

      assert context.isEventLoopContext();
      statsTimerId = vertx.setPeriodic(benchmark.statisticsCollectionPeriod(), timerId -> {
         runner.visitStatistics(requestStatsSender);
         requestStatsSender.send(false, statisticsCountDown);
         runner.visitSessionPoolStats(sessionStatsSender);
         sessionStatsSender.send();
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
