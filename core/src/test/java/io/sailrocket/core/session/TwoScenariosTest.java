package io.sailrocket.core.session;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.builders.SequenceBuilder;
import io.sailrocket.core.util.RandomConcurrentSet;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TwoScenariosTest extends BaseScenarioTest {
   private ConcurrentMap<String, SailsState> serverState = new ConcurrentHashMap<>();

   @Override
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
   public void testRandomDecision() throws BenchmarkDefinitionException {
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

      ScenarioBuilder rigScenario = scenarioBuilder();
      {
         SequenceBuilder selectShip = sequenceBuilder("select-ship")
               .step().stopwatch()
                  .step().poll(ships::fetch, "ship")
                     .filter(ship -> ship.sailsState == SailsState.FURLED, ships::put)
                  .endStep()
                  .end()
               .step().nextSequence("board")
               .end();

         SequenceBuilder board = sequenceBuilder("board")
               .step().httpRequest(HttpMethod.GET).path("/board").endStep()
               .step().awaitAllResponses()
               .step().nextSequence("rig")
               .end();

         SequenceBuilder rig = sequenceBuilder("rig")
               .step().httpRequest(HttpMethod.GET).pathGenerator(s -> "/rig?ship=" + encode(((ShipInfo) s.getObject("ship")).name))
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
               .end();

         SequenceBuilder disembark = sequenceBuilder("disembark")
               .step().httpRequest(HttpMethod.GET).path("/disembark").endStep()
               .step().awaitAllResponses()
               .step(s -> ships.put((ShipInfo) s.getObject("ship")))
               .end();

         rigScenario
               .initialSequence(selectShip)
               .sequence(board)
               .sequence(rig)
               .sequence(disembark);
      }

      ScenarioBuilder furlScenario = scenarioBuilder();
      {
         SequenceBuilder selectShip = sequenceBuilder("select-ship")
               .step().stopwatch()
                  .step().poll(ships::fetch, "ship")
                     .filter(ship -> ship.sailsState == SailsState.RIGGED, ships::put)
                  .endStep()
                  .end()
               .step().nextSequence("board")
               .end();

         SequenceBuilder board = sequenceBuilder("board")
               .step().httpRequest(HttpMethod.GET).path("/board").endStep()
               .step().awaitAllResponses()
               .step().nextSequence("furl")
               .end();

         SequenceBuilder furl = sequenceBuilder("furl")
               .step().httpRequest(HttpMethod.GET).pathGenerator(s -> "/furl?ship=" + encode(((ShipInfo) s.getObject("ship")).name))
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
               .end();

         SequenceBuilder disembark = sequenceBuilder("disembark")
               .step().httpRequest(HttpMethod.GET).path("/disembark").endStep()
               .step().awaitAllResponses()
               .step(s -> ships.put((ShipInfo) s.getObject("ship")))
               .end();

         furlScenario
               .initialSequence(selectShip)
               .sequence(board)
               .sequence(furl)
               .sequence(disembark);
      }

      Simulation simulation = simulationBuilder()
            .http(httpBuilder().baseUrl("http://localhost:8080"))
            .concurrency(10)
            .connections(10)
            .addPhase("rig").constantPerSec(3)
               .duration(5000)
               .maxDuration(10000)
               .scenario(rigScenario)
               .endPhase()
            .addPhase("furl").constantPerSec(2) // intentionally less to trigger maxDuration
               .duration(5000)
               .maxDuration(10000)
               .scenario(furlScenario)
               .endPhase()
            .build();

      BenchmarkImpl benchmark = BenchmarkBuilder.builder()
            .name("Test Benchmark")
            .simulation(simulation)
            .build();

      benchmark.run();
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
