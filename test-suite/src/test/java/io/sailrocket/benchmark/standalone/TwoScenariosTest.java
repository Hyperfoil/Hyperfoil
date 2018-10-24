package io.sailrocket.benchmark.standalone;

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

import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.impl.LocalSimulationRunner;
import io.sailrocket.core.util.RandomConcurrentSet;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
@Category(io.sailrocket.test.Benchmark.class)
public class TwoScenariosTest {
   protected Vertx vertx;
   protected Router router;

   private ConcurrentMap<String, SailsState> serverState = new ConcurrentHashMap<>();

   @Before
   public void before(TestContext ctx) {
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      vertx.createHttpServer().requestHandler(router::accept).listen(8080, "localhost", ctx.asyncAssertSuccess());
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

      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
         .name("Test Benchmark")
         .simulation()
            .http().baseUrl("http://localhost:8080").endHttp()
            .concurrency(10)
            .connections(10)
            .addPhase("rig").constantPerSec(3)
               .duration(5000)
               .maxDuration(10000)
               .scenario()
                  .initialSequence("select-ship")
                     .step().stopwatch()
                        .step().poll(ships::fetch, "ship")
                        .filter(ship -> ship.sailsState == SailsState.FURLED, ships::put)
                        .endStep()
                     .end()
                     .step().nextSequence("board")
                  .endSequence()
                  .sequence("board")
                     .step().httpRequest(HttpMethod.GET).path("/board").endStep()
                     .step().awaitAllResponses()
                     .step().nextSequence("rig")
                  .endSequence()
                  .sequence("rig")
                     .step().httpRequest(HttpMethod.GET)
                        .pathGenerator(s -> "/rig?ship=" + encode(((ShipInfo) s.getObject("ship")).name))
                        .handler().statusValidator(((session, status) -> {
                           if (status == 200) {
                              ((ShipInfo) session.getObject("ship")).sailsState = SailsState.RIGGED;
                              return true;
                           }
                           return false;
                        })).endHandler()
                     .endStep()
                     .step().awaitAllResponses()
                     .step().nextSequence("disembark")
                  .endSequence()
                  .sequence("disembark")
                     .step().httpRequest(HttpMethod.GET).path("/disembark").endStep()
                     .step().awaitAllResponses()
                     .step(s -> {
                        ships.put((ShipInfo) s.getObject("ship"));
                        return true;
                     })
                  .endSequence()
               .endScenario()
            .endPhase()
            .addPhase("furl").constantPerSec(2) // intentionally less to trigger maxDuration
               .duration(5000) // no max duration, should not need it
               .scenario()
                  .initialSequence("select-ship")
                     .step().stopwatch()
                        .step().poll(ships::fetch, "ship")
                        .filter(ship -> ship.sailsState == SailsState.RIGGED, ships::put)
                        .endStep()
                     .end()
                     .step().nextSequence("board")
                  .endSequence()
                  .sequence("board")
                     .step().httpRequest(HttpMethod.GET).path("/board").endStep()
                     .step().awaitAllResponses()
                     .step().nextSequence("furl")
                  .endSequence()
                  .sequence("furl")
                     .step().httpRequest(HttpMethod.GET)
                        .pathGenerator(s -> "/furl?ship=" + encode(((ShipInfo) s.getObject("ship")).name))
                        .handler().statusValidator((session, status) -> {
                           if (status == 200) {
                              ((ShipInfo) session.getObject("ship")).sailsState = SailsState.RIGGED;
                              return true;
                           }
                           return false;
                        }).endHandler()
                     .endStep()
                     .step().awaitAllResponses()
                     .step().nextSequence("disembark")
                  .endSequence()
                  .sequence("disembark")
                     .step().httpRequest(HttpMethod.GET).path("/disembark").endStep()
                     .step().awaitAllResponses()
                     .step(s -> {
                        ships.put((ShipInfo) s.getObject("ship"));
                        return true;
                     })
                  .endSequence()
               .endScenario()
            .endPhase()
         .endSimulation();

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
