package io.hyperfoil.http;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpVersion;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HttpDecodeSpaceTest extends BaseClientTest {

   @Test
   public void testSimpleHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping");
   }

   @Test
   public void testSimpleHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping");
   }

   @Test
   public void testSpaceBeforeHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceBeforeHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping pong?rules");
   }

   @Test
   public void testSpaceAfterHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceAfterHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping?pong rules");
   }

   @Test
   public void testSpaceBeforeAfterHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpaceBeforeAfterHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping pong?rules one");
   }

   @Test
   public void testSpacesHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testSpacesHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/ping ping pong pong?rules one two three four");
   }

   @Test
   public void testComplexHttp1x(TestContext ctx) {
      test(ctx, HTTP1x_ONLY, "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   @Test
   public void testComplexHttp2(TestContext ctx) {
      test(ctx, HTTP2_ONLY, "/oidc/endpoint/OP/authorize me?client_id=nc4b29d8d4myasad9a9ptn9ossihjs1y&response_type=code&scope=openid email profile&redirect_uri=https://cp-console.mosss-f6522d190538f009b13c287376c6106d-0000.us-east.containers.appdomain.cloud:443/auth/liberty/callback&state=1611152679");
   }

   private void test(TestContext ctx, List<io.vertx.core.http.HttpVersion> serverVersions, String path) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, serverVersions, HttpDecodeSpaceTest::isPathCorrect,
            (client, async) -> sendRequestAndAssertStatus(ctx, client, async, HttpMethod.GET, path, 200));
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
