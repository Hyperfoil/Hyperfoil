package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static io.hyperfoil.core.session.SessionFactory.access;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.handlers.ArrayRecorder;
import io.hyperfoil.core.handlers.ProcessorAssertion;
import io.hyperfoil.core.handlers.JsonHandler;
import io.hyperfoil.core.steps.AddToIntAction;
import io.hyperfoil.core.steps.AwaitConditionStep;
import io.hyperfoil.core.steps.SetAction;
import io.hyperfoil.core.steps.SetIntAction;
import io.hyperfoil.core.test.CrewMember;
import io.hyperfoil.core.test.Fleet;
import io.hyperfoil.core.test.Ship;
import io.hyperfoil.function.SerializableFunction;
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
   private static final String NUMBER_OF_SHIPS = "numberOfShips";
   private static final String NUMBER_OF_SUNK_SHIPS = "numberOfSunkShips";

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

      // @formatter:off
      scenario(2)
            .intVar(NUMBER_OF_SUNK_SHIPS)
            .initialSequence("fleet")
               .step(SC).action(new SetIntAction.Builder().var(NUMBER_OF_SUNK_SHIPS).value(0))
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/fleet")
                  .sync(false)
                  .handler()
                     .body(HttpRequestProcessorBuilder.adapt(new JsonHandler.Builder()
                           .query(".ships[].name")
                           .processor(shipAssertion.processor(new ArrayRecorder.Builder()
                                 .toVar("shipNames")
                                 .format(DataFormat.STRING)
                                 .maxSize(MAX_SHIPS)))))
                  .endHandler()
               .endStep()
               .step(SC).action(new SetAction.Builder().var("crewCount").intArray().size(MAX_SHIPS).end())
               .step(SC).foreach()
                  .fromVar("shipNames")
                  .counterVar(NUMBER_OF_SHIPS)
                  .sequence("ship")
               .endStep()
            .endSequence()
            .sequence("ship")
               .concurrency(3)
               .step(SC).httpRequest(HttpMethod.GET)
                  .path(this::currentShipQuery)
                  .sync(false)
                  .handler()
                     .body(HttpRequestProcessorBuilder.adapt(new JsonHandler.Builder()
                           .query(".crew[]")
                           .processor(crewAssertion.processor(
                                 RequestProcessorBuilder.adapt(new AddToIntAction.Builder()
                                       .var("crewCount[.]")
                                       .value(1)
                                       .orElseSetTo(1))))))
                     // We need to make sure crewCount is set even if there's no crew
                     .onCompletion(new SetIntAction.Builder().var("crewCount[.]").value(0).onlyIfNotSet(true))
                  .endHandler()
               .endStep()
               .step(SC).breakSequence()
                  .dependency("crewCount[.]")
                  // TODO: since previous step is async we might observe a situation when crewCount[.]
                  //  is lower than the size of crew. It doesn't matter here as we're just comparing > 0.
                  //  We could use separate variable (array) for body processing completion.
                  .intCondition().fromVar("crewCount[.]").greaterThan(0).end()
                  .onBreak(new AddToIntAction.Builder().var(NUMBER_OF_SHIPS).value(-1))
               .endStep()
               .step(SC).httpRequest(HttpMethod.DELETE)
                  .path(this::currentShipQuery)
                  .sync(false)
                  .handler()
                     .status(() -> {
                        Access numberOfSunkShips = access(NUMBER_OF_SUNK_SHIPS);
                        return (request, status) -> {
                           if (status == 204) {
                              numberOfSunkShips.addToInt(request.session, -1);
                           } else {
                              ctx.fail("Unexpected status " + status);
                           }
                        };
                     })
                  .endHandler()
               .endStep()
               .step(SC).action(new AddToIntAction.Builder().var(NUMBER_OF_SUNK_SHIPS).value(1))
               .step(SC).action(new AddToIntAction.Builder().var(NUMBER_OF_SHIPS).value(-1))
            .endSequence()
            .initialSequence("final")
               .stepBuilder(new AwaitConditionStep.Builder(NUMBER_OF_SHIPS, (s, numberOfShips) -> numberOfShips.isSet(s) && numberOfShips.getInt(s) <= 0))
               .stepBuilder(new AwaitConditionStep.Builder(NUMBER_OF_SUNK_SHIPS, (s, numberOfSunkShips) -> numberOfSunkShips.getInt(s) <= 0))
               .step(s -> {
                  log.info("Test completed");
                  shipAssertion.runAssertions(ctx);
                  crewAssertion.runAssertions(ctx);
                  async.countDown();
                  return true;
               })
            .endSequence();
      // @formatter:on
      runScenario();
   }

   private SerializableFunction<Session, String> currentShipQuery() {
      Access shipName = access("shipNames[.]");
      return s1 -> "/ship?name=" + encode((String) shipName.getObject(s1));
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}