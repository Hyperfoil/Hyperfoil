package io.sailrocket.core.machine;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.extractors.ArrayRecorder;
import io.sailrocket.core.extractors.CounterArrayRecorder;
import io.sailrocket.core.extractors.DefragProcessor;
import io.sailrocket.core.extractors.JsonExtractor;
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
public class StateMachineTest {
   private static final Logger log = LoggerFactory.getLogger(StateMachineTest.class);

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
    *
    * INIT -(fetch fleet)-> AWAIT-FLEET -> BEFORE-SHIP ---(no more ships)-> AWAIT-SHIPS ---(no more ships to sink)-> AWAIT-SUNK ---> null
    *                                      ^            |                   ^            |
    *                                      |            |                   |            |
    *                                      -(fetch ship)-                   --(sink ship)-
    */
   @Test(timeout = 60000000)
   public void testSinkEmptyShips(TestContext ctx) throws InterruptedException {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async(2);
      CountDownLatch latch = new CountDownLatch(1);

      // Define states first, then link them
      State initState = new State("init");
      State awaitFleetState = new State("awaitFleet");
      State beforeShipRequest = new State("beforeShip");
      State awaitShipState = new State("awaitShip");
      State awaitSunkState = new State("awaitSunk");

//      BiConsumer<Session, HttpRequest> addAuth = (session, appendHeader) -> appendHeader.putHeader("Authentization", (String) session.var("authToken"));

      HttpResponseHandler fleetHandler = new HttpResponseHandler();
      HttpRequestAction requestFleetAction = new HttpRequestAction(HttpMethod.GET, s -> "/fleet", null, null, fleetHandler);
      Transition requestFleetTransition = new Transition(null, requestFleetAction, awaitFleetState, true);
      initState.addTransition(requestFleetTransition);

      fleetHandler.addBodyExtractor(new JsonExtractor(".ships[].name", new DefragProcessor(new ArrayRecorder("shipNames", MAX_SHIPS))));
      Action countShipsAction = s -> s
            .setInt("numberOfShips", count((String[]) s.getObject("shipNames")))
            .setInt("currentShipRequest", 0)
            .setInt("currentShipResponse", 0)
            .setInt("numberOfSunkShips", 0);
      awaitFleetState.addTransition(new Transition(null, countShipsAction, beforeShipRequest, false));

      HttpResponseHandler shipHandler = new HttpResponseHandler();
      ActionChain requestShipChain = new ActionChain(
            new HttpRequestAction(HttpMethod.GET, StateMachineTest::currentShipQuery, null, null, shipHandler),
            s -> s.addToInt("currentShipRequest", 1)
      );
      beforeShipRequest.addTransition(new Transition(s -> s.getInt("currentShipRequest") < s.getInt("numberOfShips"), requestShipChain, beforeShipRequest, false));
      beforeShipRequest.addTransition(new Transition(s -> s.getInt("numberOfShips") > 0, s -> s.setInt("currentShipRequest", 0), awaitShipState, true));
      beforeShipRequest.addTransition(new Transition(null, s -> s.setInt("currentShipRequest", 0), awaitShipState, false));

      shipHandler.addBodyExtractor(new JsonExtractor(".crew[]", new CounterArrayRecorder("crewCount", "currentShipResponse", MAX_SHIPS)));

      HttpResponseHandler sinkHandler = new HttpResponseHandler();
      HttpRequestAction sinkShipAction = new HttpRequestAction(HttpMethod.DELETE, StateMachineTest::currentShipQuery, null, null, sinkHandler);
      ActionChain sinkChain = new ActionChain(sinkShipAction, s -> s.addToInt("currentShipRequest", 1).addToInt("numberOfSunkShips", 1));

      // In order to make all other transitions to awaitShip non-blocking we'll check first if we got the response and if not, we'll wait for it first.
      awaitShipState.addTransition(new Transition(s -> s.getInt("currentShipRequest") < s.getInt("numberOfShips") && s.getInt("currentShipRequest") >= s.getInt("currentShipResponse"), null, awaitShipState, true));
      awaitShipState.addTransition(new Transition(s -> s.getInt("currentShipRequest") < s.getInt("numberOfShips") && currentCrewCount(s) == 0, sinkChain, awaitShipState, false));
      awaitShipState.addTransition(new Transition(s -> s.getInt("currentShipRequest") < s.getInt("numberOfShips"), s -> s.addToInt("currentShipRequest", 1), awaitShipState, false));
      awaitShipState.addTransition(new Transition(s -> s.getInt("numberOfSunkShips") > 0, null, awaitSunkState, true));
      awaitShipState.addTransition(new Transition(null, null, awaitSunkState, false));

      sinkHandler.addStatusExtractor(((status, session) -> {
         if (status == 204) {
            session.addToInt("numberOfSunkShips", -1);
         } else {
            ctx.fail("Unexpected status " + status);
         }
      }));

      awaitSunkState.addTransition(new Transition(s -> s.getInt("numberOfSunkShips") > 0, null, awaitSunkState, true));
      awaitSunkState.addTransition(new Transition(null, session -> {
         log.info("Test completed");
         async.countDown();
         latch.countDown();
      }, null, true));

      // Allocating init
      Session session = new Session(httpClientPool, timedExecutor, initState, 16);
      // These are the variables we use directly in lambdas; we need to make space for them
//      session.declare("authToken");
      session.declareInt("numberOfShips");
      session.declareInt("currentShipRequest");
      session.declareInt("currentShipResponse");
      session.declareInt("numberOfSunkShips");
      initState.reserve(session);
      awaitFleetState.reserve(session);
      beforeShipRequest.reserve(session);
      awaitShipState.reserve(session);
      awaitSunkState.reserve(session);

      // Allocation-free runs
      session.run();
      assertTrue(latch.await(1, TimeUnit.MINUTES));
      log.trace(session.statistics());
      session.reset(initState);
      session.run();
   }

   private int currentCrewCount(Session session) {
      return ((int[]) session.getObject("crewCount"))[session.getInt("currentShipRequest")];
   }

   private static String currentShipQuery(Session s) {
      int currentShip = s.getInt("currentShipRequest");
      String shipName = ((String[]) s.getObject("shipNames"))[currentShip];
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

   private static int count(String[] strings) {
      for (int i = 0; i < strings.length; ++i) {
         if (strings[i] == null) return i;
      }
      return strings.length;
   }
}
