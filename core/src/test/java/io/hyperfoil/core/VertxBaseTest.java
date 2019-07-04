package io.hyperfoil.core;

import java.util.ArrayList;

import org.junit.After;

import io.vertx.core.Vertx;

public class VertxBaseTest {
   protected Vertx vertx = Vertx.vertx();
   protected ArrayList<Runnable> cleanup = new ArrayList<>();

   @After
   public void cleanup() {
      cleanup.forEach(Runnable::run);
      cleanup.clear();
   }
}
