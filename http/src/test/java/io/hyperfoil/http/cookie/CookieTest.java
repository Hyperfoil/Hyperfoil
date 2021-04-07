package io.hyperfoil.http.cookie;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.statistics.HttpStats;
import io.vertx.core.http.Cookie;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CookieTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
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
      // @formatter:off
      scenario().initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test1")
                  .metric("test1")
               .endStep()
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test2")
                  .metric("test2")
                  .handler()
                     .status((request, status) -> {
                        if (status != 200) request.markInvalid();
                     })
                  .endHandler()
               .endStep()
            .endSequence();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot test1 = stats.get("test1");
      StatisticsSnapshot test2 = stats.get("test1");
      HttpStats http1 = HttpStats.get(test1);
      HttpStats http2 = HttpStats.get(test2);
      assertThat(http1.status_5xx).isEqualTo(0);
      assertThat(http1.status_2xx).isEqualTo(1);
      assertThat(http2.status_5xx).isEqualTo(0);
      assertThat(http2.status_2xx).isEqualTo(1);
   }
}
