package io.hyperfoil.benchmark.standalone;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.core.handlers.NewSequenceAction;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.RandomConcurrentSet;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
@Category(io.hyperfoil.test.Benchmark.class)
public class TwoScenariosTest extends BaseBenchmarkTest {

   protected Router router;
   private final ConcurrentMap<String, SailsState> serverState = new ConcurrentHashMap<>();

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      if (router == null) {
         router = Router.router(vertx);
         initRouter();
      }
      return router;
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

      Locator.push(TestUtil.locator());
      // We need a different accessor per phase because the indices are assigned per scenario
      ReadAccess rigShip = SessionFactory.readAccess("ship");
      ReadAccess furlShip = SessionFactory.readAccess("ship");
      Locator.pop();

      // @formatter:off
      BenchmarkBuilder benchmark = BenchmarkBuilder.builder()
            .name("Test Benchmark")
            .addPlugin(HttpPluginBuilder::new)
               .ergonomics()
                  .stopOnInvalid(false)
               .endErgonomics()
               .http()
                  .host("localhost").port(httpServer.actualPort())
                  .sharedConnections(10)
               .endHttp()
            .endPlugin()
            .addPhase("rig").constantRate(3)
               .duration(5000)
               .maxDuration(10000)
               .scenario()
                  .initialSequence("select-ship")
                     .step(SC).stopwatch()
                        .step(SC).poll(ships::fetch, "ship")
                           .filter(shipInfo -> shipInfo.sailsState == SailsState.FURLED, ships::put)
                        .endStep()
                     .end()
                     .step(SC).action(new NewSequenceAction.Builder().sequence("board"))
                  .endSequence()
                  .sequence("board")
                     .step(SC).httpRequest(HttpMethod.GET).path("/board").endStep()
                     .step(SC).action(new NewSequenceAction.Builder().sequence("rig"))
                  .endSequence()
                  .sequence("rig")
                     .step(SC).httpRequest(HttpMethod.GET)
                        .path(s -> "/rig?ship=" + encode(((ShipInfo) rigShip.getObject(s)).name))
                        .handler().status(((request, status) -> {
                           if (status == 200) {
                              ((ShipInfo) rigShip.getObject(request.session)).sailsState = SailsState.RIGGED;
                           } else {
                              request.markInvalid();
                           }
                        })).endHandler()
                     .endStep()
                     .step(SC).action(new NewSequenceAction.Builder().sequence("disembark"))
                  .endSequence()
                  .sequence("disembark")
                     .step(SC).httpRequest(HttpMethod.GET).path("/disembark").endStep()
                     .step(s -> {
                        ships.put((ShipInfo) rigShip.getObject(s));
                        return true;
                     })
                  .endSequence()
               .endScenario()
            .endPhase()
            .addPhase("furl").constantRate(2) // intentionally less to trigger maxDuration
               .duration(5000) // no max duration, should not need it
               .scenario()
               .initialSequence("select-ship")
                  .step(SC).stopwatch()
                     .step(SC).poll(ships::fetch, "ship")
                        .filter(shipInfo -> shipInfo.sailsState == SailsState.RIGGED, ships::put)
                     .endStep()
                  .end()
                  .step(SC).action(new NewSequenceAction.Builder().sequence("board"))
               .endSequence()
               .sequence("board")
                  .step(SC).httpRequest(HttpMethod.GET).path("/board").endStep()
                  .step(SC).action(new NewSequenceAction.Builder().sequence("furl"))
               .endSequence()
               .sequence("furl")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .path(s -> "/furl?ship=" + encode(((ShipInfo) furlShip.getObject(s)).name))
                     .handler().status((request, status) -> {
                        if (status == 200) {
                           ((ShipInfo) furlShip.getObject(request.session)).sailsState = SailsState.RIGGED;
                        } else {
                           request.markInvalid();
                        }
                     }).endHandler()
                  .endStep()
                  .step(SC).action(new NewSequenceAction.Builder().sequence("disembark"))
               .endSequence()
               .sequence("disembark")
                  .step(SC).httpRequest(HttpMethod.GET).path("/disembark").endStep()
                  .step(s -> {
                     ships.put((ShipInfo) furlShip.getObject(s));
                     return true;
                  })
               .endSequence()
            .endScenario()
            .endPhase();
      // @formatter:on

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
