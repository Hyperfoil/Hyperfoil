package io.hyperfoil.core.session;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.handlers.ArrayRecorder;
import io.hyperfoil.core.handlers.ProcessorAssertion;
import io.hyperfoil.core.handlers.SequenceScopedCountRecorder;
import io.hyperfoil.core.handlers.DefragProcessor;
import io.hyperfoil.core.handlers.JsonHandler;
import io.hyperfoil.core.steps.AwaitConditionStep;
import io.hyperfoil.core.test.CrewMember;
import io.hyperfoil.core.test.Fleet;
import io.hyperfoil.core.test.Ship;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class FleetTest extends BaseScenarioTest {

   private static final Fleet FLEET = new Fleet("Československá námořní flotila")
         .addBase("Hamburg")
         .addShip(new Ship("Republika")
               .dwt(10500)
               .addCrew(new CrewMember("Captain", "Adam", "Korkorán"))
               .addCrew(new CrewMember("Cabin boy", "Pepek", "Novák"))
         )
         .addShip(new Ship("Julius Fučík").dwt(7100))
         .addShip(new Ship("Lidice").dwt(8500));
   private static final int MAX_SHIPS = 16;

   @Override
   protected void initRouter() {
      router.route("/fleet").handler(routingContext -> {
         HttpServerResponse response = routingContext.response();
         response.end(Json.encodePrettily(FLEET));
      });
      router.route("/ship").handler(routingContext -> {
         String shipName = routingContext.queryParam("name").stream().findFirst().orElse("");
         Optional<Ship> ship = FLEET.getShips().stream().filter(s -> s.getName().equals(shipName)).findFirst();
         if (!ship.isPresent()) {
            routingContext.response().setStatusCode(404).end();
            return;
         }
         switch (routingContext.request().method()) {
            case GET:
               routingContext.response().end(Json.encodePrettily(ship.get()));
               break;
            case DELETE:
               routingContext.response().setStatusCode(204).setStatusMessage("Ship sunk.").end();
               break;
         }
      });
   }

   /**
    * Fetch a fleet (list of ships), then fetch each ship separately and if it has no crew, sink the ship.
    */
   @Test
   public void testSinkEmptyShips(TestContext ctx) {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async(2);

      ProcessorAssertion shipAssertion = new ProcessorAssertion(3, true);
      ProcessorAssertion crewAssertion = new ProcessorAssertion(2, true);

      Access numberOfShips = SessionFactory.access("numberOfShips");
      Access numberOfSunkShips = SessionFactory.access("numberOfSunkShips");

      scenario(2)
            .intVar("numberOfSunkShips")
            .initialSequence("fleet")
               .step(s -> {
                  numberOfSunkShips.setInt(s, 0);
                  return true;
               })
               .step(SC).httpRequest(HttpMethod.GET).path("/fleet")
                  .handler()
                     .body(new JsonHandler(".ships[].name", shipAssertion.processor(new DefragProcessor<>(new ArrayRecorder("shipNames", DataFormat.STRING, MAX_SHIPS)))))
                  .endHandler()
               .endStep()
               .step(SC).foreach("shipNames", "numberOfShips").sequence("ship").endStep()
            .endSequence()
            .sequence("ship")
               .step(SC).httpRequest(HttpMethod.GET).pathGenerator(FleetTest::currentShipQuery)
                  .handler()
                     .body(new JsonHandler(".crew[]", crewAssertion.processor(new SequenceScopedCountRecorder("crewCount", MAX_SHIPS))))
                  .endHandler()
               .endStep()
               .step(SC).breakSequence()
                  .dependency("crewCount[.]")
                  .intCondition().var("crewCount[.]").greaterThan(0).endCondition()
                  .onBreak(s -> numberOfShips.addToInt(s, -1))
               .endStep()
               .step(SC).httpRequest(HttpMethod.DELETE).pathGenerator(FleetTest::currentShipQuery)
                  .handler()
                     .status(((request, status) -> {
                        if (status == 204) {
                           numberOfSunkShips.addToInt(request.session, -1);
                        } else {
                           ctx.fail("Unexpected status " + status);
                        }
                     }))
                  .endHandler()
               .endStep()
               .step(s -> {
                  numberOfSunkShips.addToInt(s, 1);
                  numberOfShips.addToInt(s, -1);
                  return true;
               })
            .endSequence()
            .initialSequence("final")
               .step(new AwaitConditionStep(s -> numberOfShips.isSet(s) && numberOfShips.getInt(s) <= 0))
               .step(new AwaitConditionStep(s -> numberOfSunkShips.getInt(s) <= 0))
               .step(s -> {
                  log.info("Test completed");
                  shipAssertion.runAssertions(ctx);
                  crewAssertion.runAssertions(ctx);
                  async.countDown();
                  return true;
               })
            .endSequence();

      runScenario();
   }

   private static Access currentShipName = SessionFactory.access("shipNames[.]");

   private static String currentShipQuery(Session s) {
      // TODO: avoid allocations when constructing URL
      return "/ship?name=" + encode((String) currentShipName.getObject(s));
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}