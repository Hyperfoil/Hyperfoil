package io.hyperfoil.clustering;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class BaseClusteredTest {
   protected List<Vertx> servers = new ArrayList<>();

   @After
   public void teardown(TestContext ctx) {
       servers.forEach(vertx -> vertx.close(ctx.asyncAssertSuccess()));
   }

   protected void initiateClustered(VertxOptions opts, Class<? extends Verticle> verticleClass, DeploymentOptions options, TestContext ctx, Async initAsync) {
       Vertx.clusteredVertx(opts, result -> {
           if (result.succeeded()) {
               Vertx vertx = result.result();
               servers.add(vertx);
               // Codecs can be registered just once per vertx node so we can't register them in verticles
               Codecs.register(vertx);
               vertx.deployVerticle(verticleClass.getName(), options, v -> {
                   if (v.succeeded()) {
                       initAsync.countDown();
                   } else {
                       ctx.fail(v.cause());
                   }
               });
           } else {
               ctx.fail(result.cause());
           }
       });
   }
}
