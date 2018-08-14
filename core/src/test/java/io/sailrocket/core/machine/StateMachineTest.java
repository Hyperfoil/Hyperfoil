package io.sailrocket.core.machine;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.client.HttpClientProvider;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class StateMachineTest {
   private static final Logger log = LoggerFactory.getLogger(StateMachineTest.class);

   private static Vertx vertx;
   private static HttpClientPool httpClientPool;
   private static ScheduledThreadPoolExecutor timedExecutor = new ScheduledThreadPoolExecutor(1);

   @BeforeClass
   public static void before(TestContext ctx) throws Exception {
      httpClientPool = HttpClientProvider.vertx.builder().host("localhost").port(8080).concurrency(3).threads(3).size(100).build();
      vertx = Vertx.vertx();
      vertx.createHttpServer().requestHandler(req -> {
         log.info("Received request to {}, headers {}", req.uri(), req.headers());
         req.response().end();
      }).listen(8080, "localhost", ctx.asyncAssertSuccess());
   }

   @AfterClass
   public static void cleanup(TestContext ctx) {
      timedExecutor.shutdownNow();
      httpClientPool.shutdown();
      vertx.close(ctx.asyncAssertSuccess());
   }

   @Test
   public void testRequest(TestContext ctx) {
      // We need to call async() to prevent termination when the test method completes
      Async async = ctx.async();

      // Define states first, then link them
      State initState = new State("init");
      State secondRequestState = new State("req2");
      // We'll structure the states so that we wait for the second response first
      HttpResponseState awaitSecondResponseState = new HttpResponseState("await2");
      HttpResponseState awaitFirstResponseState = new HttpResponseState("await1");
      State decrementState = new State("dec");

      HttpRequestAction firstRequestAction = new HttpRequestAction(HttpMethod.GET, s -> "/", null, (session, appendHeader) -> {
         appendHeader.accept("Authentization", (String) session.var("authToken"));
      }, awaitFirstResponseState);
      Predicate<Session> testCounter = session -> ((Integer) session.var("counter")).compareTo(0) > 0;
      Transition firstRequestTransition = new Transition(testCounter, firstRequestAction, secondRequestState, false);
      initState.addTransition(firstRequestTransition);
      initState.addTransition(new Transition(null, session -> {
         log.info("Test completed");
         async.complete();
      }, null, true));

      HttpRequestAction secondRequestAction = new HttpRequestAction(HttpMethod.GET, s -> "/favicon.png", null, null, awaitSecondResponseState);
      Transition secondRequestTransition = new Transition(null, secondRequestAction, awaitSecondResponseState, true);
      secondRequestState.addTransition(secondRequestTransition);

      awaitSecondResponseState.addTransition(new Transition(null, null, awaitFirstResponseState, true));

      DelayAction delayAction = new DelayAction(1, TimeUnit.SECONDS, decrementState);
      awaitFirstResponseState.addTransition(new Transition(null, delayAction, decrementState, true));

      Action decrementAction = session -> {
         // TODO: maybe we should add primitive vars to avoid boxing
         Integer counter = (Integer) session.var("counter");
         session.var("counter", counter - 1);
      };
      decrementState.addTransition(new Transition(null, decrementAction, initState, false));

      // Allocating init
      Session session = new Session(httpClientPool, timedExecutor, initState);
      session.var("counter", 3);
      session.var("authToken", "blabbla");
      initState.register(session);
      secondRequestState.register(session);
      awaitSecondResponseState.register(session);
      awaitFirstResponseState.register(session);
      decrementState.register(session);

      // Allocation-free run
      session.run();
   }
}
