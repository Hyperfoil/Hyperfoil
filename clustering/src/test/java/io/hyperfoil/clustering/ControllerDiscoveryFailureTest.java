package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.clustering.messages.AgentHello;
import io.hyperfoil.internal.Properties;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;

/**
 * When the controller cannot process an {@link AgentHello}, the agent must be told why instead of being left to wait
 * for a reply that never comes. The agent logs the reason of a refused registration and sends hello again.
 */
public class ControllerDiscoveryFailureTest {
   private static final long TIMEOUT_MS = 20_000;

   private Vertx vertx;
   private TestController controller;

   @BeforeEach
   public void before() throws Exception {
      vertx = Vertx.vertx();
      Codecs.register(vertx);
      controller = new TestController();
      JsonObject config = new JsonObject()
            .put(Properties.CONTROLLER_HOST, "127.0.0.1")
            .put(Properties.CONTROLLER_PORT, 0);
      vertx.deployVerticle(controller, new DeploymentOptions().setConfig(config))
            .toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   @AfterEach
   public void after() throws Exception {
      vertx.close().toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
   }

   @Test
   public void shouldRefuseAHelloItCannotProcess(@TempDir Path dir) throws Exception {
      // An inconsistent state that makes the lookup of the agent by name throw.
      Run run = new Run("BAD0", dir.resolve("BAD0"), Benchmark.forTesting());
      run.agents.add(new AgentInfo(null, 0));
      CompletableFuture<Void> registered = new CompletableFuture<>();
      controller.ctx().runOnContext(nil -> {
         controller.runs.put(run.id, run);
         registered.complete(null);
      });
      registered.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

      // Without a reply the request only ends with a timeout; keep it short for the case where that is the outcome.
      DeliveryOptions options = new DeliveryOptions().setSendTimeout(5_000);
      Throwable failure = vertx.eventBus()
            .request(Feeds.DISCOVERY, new AgentHello("agent", "node", "address", run.id), options)
            .transform(result -> io.vertx.core.Future.succeededFuture(result.cause()))
            .toCompletionStage().toCompletableFuture().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

      assertInstanceOf(ReplyException.class, failure, "the hello must be answered with a failure");
      ReplyException reply = (ReplyException) failure;
      assertEquals(ReplyFailure.RECIPIENT_FAILURE, reply.failureType(), "no reply was sent: " + reply.getMessage());
      assertTrue(reply.getMessage() != null && !reply.getMessage().isEmpty());
   }

   private static class TestController extends ControllerVerticle {
      Context ctx() {
         return context;
      }
   }
}
