package io.hyperfoil.http;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpVersion;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxTestContext;

public class HttpDecodeSpaceTest extends BaseClientTest {

   @Test
   public void testSimpleHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping");
   }

   @Test
   public void testSimpleHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping");
   }

   @Test
   public void testSpaceBeforeHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceBeforeHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceAfterHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceAfterHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceBeforeAfterHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpaceBeforeAfterHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpacesHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testSpacesHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testComplexHttp1x(VertxTestContext ctx) {
      test(ctx, HTTP1x_ONLY,
            "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   @Test
   public void testComplexHttp2(VertxTestContext ctx) {
      test(ctx, HTTP2_ONLY,
            "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   private void test(VertxTestContext ctx, List<io.vertx.core.http.HttpVersion> serverVersions, String path) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, serverVersions, HttpDecodeSpaceTest::isPathCorrect,
            (client, checkpoint) -> sendRequestAndAssertStatus(ctx, client, checkpoint, HttpMethod.GET, path, 200));
   }

   private static void isPathCorrect(HttpServerRequest req) {
      int status;
      String url = req.uri();
      if (url.contains(" ")) {
         status = 600;
      } else {
         int question = url.indexOf("?");
         String subFirst = "";
         String subSecond = "";
         if (question != -1) {
            subFirst = url.substring(0, question);
            subSecond = url.substring(url.lastIndexOf("?") + 1);
            if (subFirst.contains("+")) {
               status = 601;
            } else {
               status = 200;
            }
            if (status == 200 && subSecond.contains("%20")) {
               status = 602;
            }
         } else {
            status = 200;
         }
      }
      req.response().setStatusCode(status).end("Hello");
   }
}
