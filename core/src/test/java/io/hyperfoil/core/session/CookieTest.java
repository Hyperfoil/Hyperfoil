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
      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      StatisticsSnapshot test1 = assertSingleItem(stats.get("test1"));
      StatisticsSnapshot test2 = assertSingleItem(stats.get("test1"));
      assertThat(test1.status_5xx).isEqualTo(0);
      assertThat(test1.status_2xx).isEqualTo(1);
      assertThat(test2.status_5xx).isEqualTo(0);
      assertThat(test2.status_2xx).isEqualTo(1);
   }
}
