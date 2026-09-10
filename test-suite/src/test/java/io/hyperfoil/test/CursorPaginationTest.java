package io.hyperfoil.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class CursorPaginationTest extends BaseHttpScenarioTest {

   private static final int PAGE_SIZE = 20;
   private static final int PAGE_COUNT = 5;

   private final AtomicInteger requests = new AtomicInteger();

   @Override
   protected void initRouter() {
      router.get("/v1/conversations")
            .handler(this::authenticate)
            .handler(this::validateParams)
            .handler(this::serveConversations);
   }

   @Test
   public void test() {
      Benchmark benchmark = loadScenario("scenarios/cursor-pagination.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);

      assertThat(requests.get()).isEqualTo(PAGE_COUNT);
      assertThat(stats).containsOnlyKeys("list-conversations-page");
      StatisticsSnapshot pages = stats.get("list-conversations-page");
      assertThat(pages.requestCount).isEqualTo(PAGE_COUNT);
      assertThat(pages.responseCount).isEqualTo(PAGE_COUNT);
      assertThat(pages.errors()).isZero();
   }

   private void authenticate(RoutingContext ctx) {
      String apiKey = ctx.request().getHeader("X-API-Key");
      String userId = ctx.request().getHeader("X-User-ID");
      if ("test-api-key".equals(apiKey) && userId != null) {
         ctx.next();
      } else {
         ctx.response().setStatusCode(401).end();
      }
   }

   private void validateParams(RoutingContext ctx) {
      String mode = ctx.queryParams().get("mode");
      String limit = ctx.queryParams().get("limit");
      if ("all".equals(mode) && "20".equals(limit)) {
         ctx.next();
      } else {
         ctx.response().setStatusCode(400).end();
      }
   }

   private void serveConversations(RoutingContext ctx) {
      int page = pageNumber(ctx.queryParams().get("afterCursor"));
      if (page >= 0 && page < PAGE_COUNT) {
         requests.incrementAndGet();
         JsonObject response = new JsonObject().put("conversations", conversations(page));
         if (page + 1 < PAGE_COUNT) {
            response.put("afterCursor", "page-" + (page + 1));
         }
         ctx.response()
               .putHeader("Content-Type", "application/json")
               .end(response.encode());
      } else {
         ctx.response().setStatusCode(400).end();
      }
   }

   private static int pageNumber(String afterCursor) {
      if (afterCursor == null) {
         return 0;
      }
      if (!afterCursor.startsWith("page-")) {
         return -1;
      }
      try {
         return Integer.parseInt(afterCursor.substring("page-".length()));
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private static JsonArray conversations(int page) {
      JsonArray conversations = new JsonArray();
      int firstConversation = page * PAGE_SIZE + 1;
      for (int i = 0; i < PAGE_SIZE; ++i) {
         conversations.add(new JsonObject().put("id", "conversation-%03d".formatted(firstConversation + i)));
      }
      return conversations;
   }
}
