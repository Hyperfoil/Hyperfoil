package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
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
      scenario().initialSequence("test")
               .step().httpRequest(HttpMethod.GET).path("/test1").endStep()
               .step().awaitAllResponses()
               .step()
                  .httpRequest(HttpMethod.GET).path("/test2").handler()
                     .statusValidator((request, status) -> status == 200)
                  .endHandler()
               .endStep()
               .step().awaitAllResponses()
            .endSequence();

      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      StatisticsSnapshot snapshot = assertSingleSessionStats(stats);
      assertThat(snapshot.status_5xx).isEqualTo(0);
      assertThat(snapshot.status_2xx).isEqualTo(2);
   }
}
