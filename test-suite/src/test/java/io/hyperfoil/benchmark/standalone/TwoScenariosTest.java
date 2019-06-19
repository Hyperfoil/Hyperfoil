package io.hyperfoil.benchmark.standalone;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.RandomConcurrentSet;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
@Category(io.hyperfoil.test.Benchmark.class)
public class TwoScenariosTest {
   private static Class<StepCatalog> SC = StepCatalog.class;

   protected Vertx vertx;
   protected Router router;

   private ConcurrentMap<String, SailsState> serverState = new ConcurrentHashMap<>();
   private HttpServer server;

   @Before
   public void before(TestContext ctx) {
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      server = vertx.createHttpServer().requestHandler(router).listen(0, "localhost", ctx.asyncAssertSuccess());
   }

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }

   protected void initRouter() {
      router.route("/board").handler(ctx -> ctx.response().end("Ahoy!"));
      router.route("/rig").handler(ctx -> {
         String ship = ctx.queryParam("ship").stream().findFirst().orElse(null);
         if (ship != null && serverState.replace(ship, SailsState.FURLED, SailsState.RIGGED)) {
            ctx.response().end("Rigged!");
         } else {
            ctx.response().setStatusCode(409).end();
         }
      });
      router.route("/furl").handler(ctx -> {
         String ship = ctx.queryParam("ship").stream().findFirst().orElse(null);
         if (ship != null && serverState.replace(ship, SailsState.RIGGED, SailsState.FURLED)) {
            ctx.response().end("Furled!");
         } else {
            ctx.response().setStatusCode(409).end();
         }
      });
      router.route("/disembark").handler(ctx -> ctx.response().end("Bye!"));
   }

   @Test
   public void testTwoScenarios() {
      // We have two options when simulating 'some users rigging and sailing ships'
      // * shared state of all ships (requires synchronization)
      // ** one option is to pick id from pool at the beginning
      // ** we should measure the contention there
      // * indexing users/sessions by uniqueId and keeping state per active user
      // ** however sessions for different phases have always different uniqueId()
      // When we can't find a suitable ship, we can
      // * try to pick another scenario - not possible because phases are fully isolated
      // * loop until we find suitable ship - do some yielding/backoff
      // * declare failure in stats (and retry later)

      RandomConcurrentSet<ShipInfo> ships = new RandomConcurrentSet<>(16);
      ships.put(new ShipInfo("Niña", SailsState.FURLED));
      ships.put(new ShipInfo("Pinta", SailsState.FURLED));
      ships.put(new ShipInfo("Santa María", SailsState.RIGGED));
      ships.put(new ShipInfo("Trinidad", SailsState.RIGGED));
      ships.put(new ShipInfo("Antonio", SailsState.FURLED));
      ships.put(new ShipInfo("Concepción", SailsState.RIGGED));
      ships.put(new ShipInfo("Santiago", SailsState.FURLED));
      ships.put(new ShipInfo("Victoria", SailsState.FURLED));

      Access ship = SessionFactory.access("ship");

      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
         .name("Test Benchmark")
         .http()
            .host("localhost").port(server.actualPort())
            .sharedConnections(10)
         .endHttp()
         .addPhase("rig").constantPerSec(3)
            .duration(5000)
            .maxDuration(10000)
            .scenario()
               .initialSequence("select-ship")
                  .step(SC).stopwatch()
                     .step(SC).poll(ships::fetch, "ship")
                     .filter(shipInfo -> shipInfo.sailsState == SailsState.FURLED, ships::put)
                     .endStep()
                  .end()
                  .step(SC).nextSequence("board")
               .endSequence()
               .sequence("board")
                  .step(SC).httpRequest(HttpMethod.GET).path("/board").endStep()
                  .step(SC).nextSequence("rig")
               .endSequence()
               .sequence("rig")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .pathGenerator(s -> "/rig?ship=" + encode(((ShipInfo) ship.getObject(s)).name))
                     .handler().status(((request, status) -> {
                        if (status == 200) {
                           ((ShipInfo) ship.getObject(request.session)).sailsState = SailsState.RIGGED;
                        } else {
                           request.markInvalid();
                        }
                     })).endHandler()
                  .endStep()
                  .step(SC).nextSequence("disembark")
               .endSequence()
               .sequence("disembark")
                  .step(SC).httpRequest(HttpMethod.GET).path("/disembark").endStep()
                  .step(s -> {
                     ships.put((ShipInfo) ship.getObject(s));
                     return true;
                  })
               .endSequence()
            .endScenario()
         .endPhase()
         .addPhase("furl").constantPerSec(2) // intentionally less to trigger maxDuration
            .duration(5000) // no max duration, should not need it
            .scenario()
               .initialSequence("select-ship")
                  .step(SC).stopwatch()
                     .step(SC).poll(ships::fetch, "ship")
                     .filter(shipInfo -> shipInfo.sailsState == SailsState.RIGGED, ships::put)
                     .endStep()
                  .end()
                  .step(SC).nextSequence("board")
               .endSequence()
               .sequence("board")
                  .step(SC).httpRequest(HttpMethod.GET).path("/board").endStep()
                  .step(SC).nextSequence("furl")
               .endSequence()
               .sequence("furl")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .pathGenerator(s -> "/furl?ship=" + encode(((ShipInfo) ship.getObject(s)).name))
                     .handler().status((request, status) -> {
                        if (status == 200) {
                           ((ShipInfo) ship.getObject(request.session)).sailsState = SailsState.RIGGED;
                        } else {
                           request.markInvalid();
                        }
                     }).endHandler()
                  .endStep()
                  .step(SC).nextSequence("disembark")
               .endSequence()
               .sequence("disembark")
                  .step(SC).httpRequest(HttpMethod.GET).path("/disembark").endStep()
                  .step(s -> {
                     ships.put((ShipInfo) ship.getObject(s));
                     return true;
                  })
               .endSequence()
            .endScenario()
         .endPhase();

      new LocalSimulationRunner(benchmark.build()).run();
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }

   enum SailsState {
      FURLED,
      RIGGED
   }

   static class ShipInfo {
      String name;
      SailsState sailsState;

      ShipInfo(String name, SailsState sailsState) {
         this.name = name;
         this.sailsState = sailsState;
      }

      @Override
      public String toString() {
         return "ShipInfo{" +
               "name='" + name + '\'' +
               ", sailsState=" + sailsState +
               '}';
      }
   }
}
