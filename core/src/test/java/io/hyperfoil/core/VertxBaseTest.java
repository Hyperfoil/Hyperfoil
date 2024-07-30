package io.hyperfoil.core;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class VertxBaseTest {
   protected Vertx vertx = Vertx.vertx();
   protected ArrayList<Runnable> cleanup = new ArrayList<>();

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
