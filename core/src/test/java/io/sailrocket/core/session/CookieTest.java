package io.sailrocket.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.StatisticsSnapshot;
import io.sailrocket.core.builders.ScenarioBuilder;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.handler.CookieHandler;

@RunWith(VertxUnitRunner.class)
public class CookieTest extends BaseScenarioTest {
   @Override
   protected void initRouter() {
      router.route().handler(CookieHandler.create());
      router.route("/test1").handler(ctx -> {
         ctx.addCookie(Cookie.cookie("foo", "bar"));
         ctx.response().end("Hello!");
      });
      router.route("/test2").handler(ctx -> {
         Cookie cookie = ctx.getCookie("foo");
         int status = cookie != null && cookie.getValue().equals("bar") ? 200 : 500;
         ctx.response().setStatusCode(status).end();
      });
   }

   @Test
   public void testRepeatCookie() {
      ScenarioBuilder scenarioBuilder = scenarioBuilder()
            .initialSequence("test")
               .step().httpRequest(HttpMethod.GET).path("/test1").endStep()
               .step().awaitAllResponses()
               .step()
                  .httpRequest(HttpMethod.GET).path("/test2").handler()
                     .statusValidator((session, status) -> status == 200)
                  .endHandler()
               .endStep()
               .step().awaitAllResponses()
            .endSequence();

      List<Session> sessions = runScenario(scenarioBuilder, 1);
      assertThat(sessions.size()).isEqualTo(1);
      Session session = sessions.iterator().next();
      assertThat(session.statistics()).isNotNull();
      assertThat(session.statistics().length).isEqualTo(1);
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      session.statistics()[0].moveIntervalTo(snapshot);
      assertThat(snapshot.status_5xx).isEqualTo(0);
      assertThat(snapshot.status_2xx).isEqualTo(2);
   }
}
