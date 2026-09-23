package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.internal.Controller;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Deploys a real controller and exercises benchmark registration around the request body limit.
 * The limit is passed through the verticle config, so no system properties are touched; the root directory
 * is set for the whole test JVM by surefire (see pom.xml) because {@link Controller#ROOT_DIR} is read only once.
 */
public class ControllerUploadTest {
   private static final int MIB = 1024 * 1024;

   Vertx vertx;
   RestClient client;

   @AfterEach
   public void closeController() throws Exception {
      if (client != null) {
         client.close();
      }
      if (vertx != null) {
         vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
      }
   }

   @Test
   public void registerMultipartBenchmarkAboveDefaultLimit() throws Exception {
      startController(new JsonObject().put(Properties.CONTROLLER_MAX_BODY_SIZE, 32L * MIB));
      registerAndCheck("upload-above-default", (int) Controller.DEFAULT_MAX_BODY_SIZE + MIB);
   }

   @Test
   public void defaultLimitStillRejectsOversizedUploads() throws Exception {
      startController(new JsonObject());
      assertRejected("upload-rejected-default", (int) Controller.DEFAULT_MAX_BODY_SIZE + MIB);
   }

   @Test
   public void configuredLimitRejectsOversizedUploads() throws Exception {
      startController(new JsonObject().put(Properties.CONTROLLER_MAX_BODY_SIZE, 128 * 1024));
      assertRejected("upload-rejected-configured", 256 * 1024);
   }

   @Test
   public void configuredLimitAcceptsSmallerUploads() throws Exception {
      startController(new JsonObject().put(Properties.CONTROLLER_MAX_BODY_SIZE, 128 * 1024));
      registerAndCheck("upload-below-configured", 64 * 1024);
   }

   @Test
   public void invalidLimitFailsStartup() {
      ExecutionException exception = assertThrows(ExecutionException.class,
            () -> startController(new JsonObject().put(Properties.CONTROLLER_MAX_BODY_SIZE, 0)));
      assertTrue(exception.getCause() instanceof IllegalArgumentException, exception::toString);
      assertTrue(exception.getCause().getMessage().contains(Properties.CONTROLLER_MAX_BODY_SIZE), exception::toString);
   }

   private void startController(JsonObject config) throws Exception {
      vertx = Vertx.vertx();
      ControllerVerticle controller = new ControllerVerticle();
      config.put(Properties.CONTROLLER_HOST, "localhost").put(Properties.CONTROLLER_PORT, 0);
      vertx.deployVerticle(controller, new DeploymentOptions().setConfig(config))
            .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
      client = new RestClient(vertx, "localhost", controller.actualPort(), false, false, null);
   }

   private void registerAndCheck(String name, int size) {
      byte[] payload = new byte[size];
      Arrays.fill(payload, (byte) 0x5a);
      payload[size - 1] = 0x7f;
      var benchmark = client.register(source(name), Map.of("payload.bin", payload), null, null);
      assertTrue(benchmark.exists());
      assertArrayEquals(payload, benchmark.files().get("payload.bin"));
      // don't leave the stored benchmark for the next test/run
      assertTrue(benchmark.forget());
      assertFalse(benchmark.exists());
   }

   private void assertRejected(String name, int size) {
      RestClientException exception = assertThrows(RestClientException.class,
            () -> client.register(source(name), Map.of("payload.bin", new byte[size]), null, null));
      assertTrue(exception.getMessage().contains("413"), exception::getMessage);
      assertFalse(client.benchmark(name).exists());
   }

   private static String source(String name) {
      // Keep the attachment unused: registration size limits should not depend on payload traversal during scenario building.
      return """
            name: %s
            phases:
            - test:
                atOnce:
                  users: 1
                  scenario:
                  - main:
                    - noop
            """.formatted(name);
   }
}
