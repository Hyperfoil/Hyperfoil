package io.hyperfoil.http;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpVersion;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class HttpVersionsTest extends BaseClientTest {
   @Test
   public void testAlpnUpgrade(VertxTestContext ctx) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, 200);
   }

   @Test
   public void testAlpnKeep(VertxTestContext ctx) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, 500);
   }

   @Test
   public void testAlpnForceHttp2(VertxTestContext ctx) {
      test(ctx, true, new HttpVersion[] { HttpVersion.HTTP_2_0 }, HTTP2_ONLY, 200);
   }

   @Test
   public void testAlpnForceHttp2ServerKeep(VertxTestContext ctx) {
      test(ctx, true, new HttpVersion[] { HttpVersion.HTTP_2_0 }, HTTP1x_ONLY, HttpVersionsTest::requireHttp2,
            ctx.failingThenComplete());
   }

   @Test
   public void testAlpnForceHttp1x(VertxTestContext ctx) {
      test(ctx, true, new HttpVersion[] { HttpVersion.HTTP_1_1 }, HTTP2_ONLY, HttpVersionsTest::requireHttp2,
            ctx.failingThenComplete());
   }

   @Test
   public void testH2cUpgrade(VertxTestContext ctx) {
      test(ctx, false, new HttpVersion[] { HttpVersion.HTTP_2_0 }, HTTP2_ONLY, 200);
   }

   @Test
   public void testCleartextDefault(VertxTestContext ctx) {
      test(ctx, false, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, 500);
   }

   @Test
   public void testCleartextDefaultServer1x(VertxTestContext ctx) {
      test(ctx, false, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, 500);
   }

   @Test
   public void testCleartextForceHttp1x(VertxTestContext ctx) {
      test(ctx, false, new HttpVersion[] { HttpVersion.HTTP_1_1 }, HTTP2_ONLY, 500);
   }

   private void test(VertxTestContext ctx, boolean ssl, HttpVersion[] clientVersions,
         List<io.vertx.core.http.HttpVersion> serverVersions, int expectedStatus) {
      test(ctx, ssl, clientVersions, serverVersions, HttpVersionsTest::requireHttp2,
            (client, checkpoint) -> sendRequestAndAssertStatus(ctx, client, checkpoint, HttpMethod.GET, "/ping",
                  expectedStatus));
   }

   private static void requireHttp2(HttpServerRequest req) {
      if (req.version() != io.vertx.core.http.HttpVersion.HTTP_2) {
         req.response().setStatusCode(500).end("HTTP/2 required.");
      } else {
         req.response().setStatusCode(200).end("Hello");
      }
   }
}
