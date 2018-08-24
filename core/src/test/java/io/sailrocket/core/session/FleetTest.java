package io.sailrocket.core.session;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Session;
import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.extractors.ArrayRecorder;
import io.sailrocket.core.extractors.SequenceScopedCountRecorder;
import io.sailrocket.core.extractors.DefragProcessor;
import io.sailrocket.core.extractors.JsonExtractor;
import io.sailrocket.core.steps.AwaitConditionStep;
import io.sailrocket.core.steps.BreakSequenceStep;
import io.sailrocket.core.steps.ForeachStep;
import io.sailrocket.core.steps.HttpRequestStep;
import io.sailrocket.core.test.CrewMember;
import io.sailrocket.core.test.Fleet;
import io.sailrocket.core.test.Ship;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
public class FleetTest {
   private static final Logger log = LoggerFactory.getLogger(FleetTest.class);

   private static final Fleet FLEET = new Fleet("Československá námořní flotila")
         .addBase("Hamburg")
         .addShip(new Ship("Republika")
               .dwt(10500)
               .addCrew(new CrewMember("Captain", "Adam", "Korkorán"))
               .addCrew(new CrewMember("Cabin boy", "Pepek", "Novák"))
         )
         .addShip(new Ship("Julius Fučík").dwt(7100))
         .addShip(new Ship("Lidice").dwt(8500));
   public static final int MAX_SHIPS = 16;

   private static Vertx vertx;
   private static HttpClientPool httpClientPool;
   private static ScheduledThreadPoolExecutor timedExecutor = new ScheduledThreadPoolExecutor(1);
   private static Router router;

   @BeforeClass
   public static void before(TestContext ctx) throws Exception {
      httpClientPool = HttpClientProvider.netty.builder().host("localhost")
            .protocol(HttpVersion.HTTP_1_1)
            .port(8080)
            .concurrency(3)
            .threads(3)
            .size(100)
            .build();
      Async clientPoolAsync = ctx.async();
      httpClientPool.start(nil -> clientPoolAsync.complete());
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      vertx.createHttpServer().requestHandler(router::accept).listen(8080, "localhost", ctx.asyncAssertSuccess());
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

   @AfterClass
   public static void cleanup(TestContext ctx) {
      timedExecutor.shutdownNow();
      httpClientPool.shutdown();
      vertx.close(ctx.asyncAssertSuccess());
   }

   /**
    * Fetch a fleet (list of ships), then fetch each ship separately and if it has no crew, sink the ship.
    */
   @Test
   public void testSinkEmptyShips(TestContext ctx) throws InterruptedException {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async(2);
      CountDownLatch latch = new CountDownLatch(1);

//      BiConsumer<Session, HttpRequest> addAuth = (session, appendHeader) -> appendHeader.putHeader("Authentization", (String) session.var("authToken"));

      ScenarioBuilder scenarioBuilder = ScenarioBuilder.scenarioBuilder();
      SequenceImpl fleetSequence = new SequenceImpl("fleet");
      SequenceImpl shipSequence = new SequenceImpl("ship");
      SequenceImpl finalSequence = new SequenceImpl("final");
      scenarioBuilder.initialSequence(fleetSequence).initialSequence(finalSequence);
      scenarioBuilder.sequence(shipSequence);

      scenarioBuilder.intVar("numberOfSunkShips");
      fleetSequence.step(s -> s.setInt("numberOfSunkShips", 0));

      fleetSequence.step(
            HttpRequestStep.builder(HttpMethod.GET).path("/fleet")
            .handler()
               .bodyExtractor(new JsonExtractor(".ships[].name", new DefragProcessor(new ArrayRecorder("shipNames", MAX_SHIPS))))
            .endHandler()
            .build());

      fleetSequence.step(new ForeachStep("shipNames", "numberOfShips", shipSequence));

      /// Ship sequence
      shipSequence.step(
         HttpRequestStep.builder(HttpMethod.GET).pathGenerator(FleetTest::currentShipQuery)
         .handler()
            .bodyExtractor(new JsonExtractor(".crew[]", new SequenceScopedCountRecorder("crewCount", MAX_SHIPS)))
         .endHandler()
         .build());

      BreakSequenceStep breakSequenceStep = new BreakSequenceStep(s -> currentCrewCount(s) > 0, s -> s.addToInt("numberOfShips", -1));
      breakSequenceStep.addDependency(new SequenceScopedVarReference("crewCount"));
      shipSequence.step(breakSequenceStep);

      shipSequence.step(
         HttpRequestStep.builder(HttpMethod.DELETE).pathGenerator(FleetTest::currentShipQuery)
         .handler()
            .statusExtractor(((status, session) -> {
               if (status == 204) {
                  session.addToInt("numberOfSunkShips", -1);
               } else {
                  ctx.fail("Unexpected status " + status);
               }
            }))
         .endHandler()
         .build());
      shipSequence.step(s -> s.addToInt("numberOfSunkShips", 1).addToInt("numberOfShips", -1));

      finalSequence.step(new AwaitConditionStep(s -> s.isSet("numberOfShips") && s.getInt("numberOfShips") <= 0));
      finalSequence.step(new AwaitConditionStep(s -> s.getInt("numberOfSunkShips") <= 0));
      finalSequence.step(s -> {
         log.info("Test completed");
         async.countDown();
         log.trace(s.statistics());
      });

      // Allocating init
      Session session = SessionFactory.create(httpClientPool, 16, 18, () -> async.count() <= 0, scenarioBuilder.build());

      // Allocation-free runs
      session.proceed();
   }

   private int currentCrewCount(Session session) {
      return ((IntVar[]) session.getObject("crewCount"))[session.currentSequence().index()].get();
   }

   private static String currentShipQuery(Session s) {
      String shipName = (String) (((ObjectVar[]) s.getObject("shipNames"))[s.currentSequence().index()]).get();
      // TODO: avoid allocations when constructing URL
      return "/ship?name=" + encode(shipName);
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}