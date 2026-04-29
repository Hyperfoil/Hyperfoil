package io.hyperfoil.core;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class VertxBaseTest {
   protected Vertx vertx;
   protected ArrayList<Runnable> cleanup = new ArrayList<>();

   @BeforeAll
   public static void setupPooledBuffers() {
      // Vert.x 5 requires explicit configuration to use pooled buffers
      System.setProperty("vertx.buffer.pooled", "true");
   }

   @BeforeEach
   public void before(Vertx vertx) {
      this.vertx = vertx;
   }

   @AfterEach
   public void cleanup() {
      cleanup.forEach(Runnable::run);
      cleanup.clear();
   }
}
