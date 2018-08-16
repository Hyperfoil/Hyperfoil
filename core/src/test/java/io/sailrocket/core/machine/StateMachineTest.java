package io.sailrocket.core.machine;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.NoSuchElementException;
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
import io.sailrocket.core.extractors.CountRecorder;
import io.sailrocket.core.extractors.JsonExtractor;
import io.sailrocket.core.extractors.SimpleRecorder;
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
   @Test
   public void testSinkEmptyShips(TestContext ctx) throws InterruptedException {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async(2);
      CountDownLatch latch = new CountDownLatch(1);

      // Define states first, then link them
      State initState = new State("init");
      HttpResponseState awaitFleetState = new HttpResponseState("awaitFleet");
      State beforeShipRequest = new State("beforeShip");
      HttpResponseState awaitShipState = new HttpResponseState("awaitShip");
      HttpResponseState awaitSunkState = new HttpResponseState("awaitSunk");

//      BiConsumer<Session, HttpRequest> addAuth = (session, appendHeader) -> appendHeader.putHeader("Authentization", (String) session.var("authToken"));

      HttpRequestAction requestFleetAction = new HttpRequestAction(HttpMethod.GET, s -> "/fleet", null, null, awaitFleetState);
      Transition requestFleetTransition = new Transition(null, requestFleetAction, awaitFleetState, true);
      initState.addTransition(requestFleetTransition);

      awaitFleetState.addBodyExtractor(new JsonExtractor(".ships[].name", new ArrayRecorder<>("shipNames", () -> new String[16])));
      Action countShipsAction = s -> s
            .setInt("shipCount", count((String[]) s.getObject("shipNames")))
            .setInt("sunkCount", 0);
      awaitFleetState.addTransition(new Transition(null, countShipsAction, beforeShipRequest, false));

      HttpRequestAction requestShipAction = new HttpRequestAction(HttpMethod.GET,
            // TODO: avoid allocations when constructing URL
            s -> "/ship?name=" + encode(fetchNext((String[]) s.getObject("shipNames"))), null, null, awaitShipState);

      beforeShipRequest.addTransition(new Transition(s -> hasNext((String[]) s.getObject("shipNames")), requestShipAction, beforeShipRequest, false));
      beforeShipRequest.addTransition(new Transition(s -> s.getInt("shipCount") > 0, null, awaitShipState, true));
      beforeShipRequest.addTransition(new Transition(null, null, awaitShipState, false));

      awaitShipState.addBodyExtractor(new JsonExtractor(".crew[]", new CountRecorder("crewCount")));
      awaitShipState.addBodyExtractor(new JsonExtractor(".name", new SimpleRecorder("shipName")));

      HttpRequestAction sinkShipAction = new HttpRequestAction(HttpMethod.DELETE, s -> "/ship?name=" + encode((String) s.getObject("shipName")), null, null, awaitSunkState);
      ActionChain sinkChain = new ActionChain(s -> s.addToInt("shipCount", -1).addToInt("sunkCount", 1), sinkShipAction);
      // This transition is blocking; we won't fire another sink ship request until someone (get ship response) wakes us up
      awaitShipState.addTransition(new Transition(s -> s.getInt("shipCount") == 1 && s.getInt("crewCount") > 0, sinkChain, awaitSunkState, false));
      awaitShipState.addTransition(new Transition(s -> s.getInt("crewCount") > 0, sinkChain, awaitShipState, true));
      awaitShipState.addTransition(new Transition(s -> s.getInt("shipCount") < 1, null, awaitSunkState, false));
      awaitShipState.addTransition(new Transition(null, s -> s.addToInt("shipCount", -1), awaitShipState, true));

      awaitSunkState.addTransition(new Transition(s -> s.getInt("sunkCount") > 1, s -> s.addToInt("sunkCount", -1), awaitSunkState, true));
      awaitSunkState.addTransition(new Transition(null, session -> {
         log.info("Test completed");
         async.countDown();
         latch.countDown();
      }, null, true));

      // Allocating init
      Session session = new Session(httpClientPool, timedExecutor, initState);
      // These are the variables we use directly in lambdas; we need to make space for them
      session.declare("authToken");
      session.declareInt("shipCount");
      session.declareInt("sunkCount");
      initState.register(session);
      awaitFleetState.register(session);
      beforeShipRequest.register(session);
      awaitShipState.register(session);
      awaitSunkState.register(session);

      // Allocation-free runs
      session.run();
      assertTrue(latch.await(1, TimeUnit.MINUTES));
      session.reset(initState);
      session.run();
   }

   private String encode(String string) {
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

   private static boolean hasNext(String[] strings) {
      for (String string : strings) {
         if (string != null) return true;
      }
      return false;
   }

   private static String fetchNext(String[] strings) {
      for (int i = 0; i < strings.length; ++i) {
         if (strings[i] != null) {
            String tmp = strings[i];
            strings[i] = null;
            return tmp;
         }
      }
      throw new NoSuchElementException();
   }
}
