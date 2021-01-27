package io.hyperfoil.core.client;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpVersion;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HttpVersionsTest extends BaseClientTest {
   @Test
   public void testAlpnUpgrade(TestContext ctx) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, 200);
   }

   @Test
   public void testAlpnKeep(TestContext ctx) {
      test(ctx, true, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, 500);
   }

   @Test
   public void testAlpnForceHttp2(TestContext ctx) {
      test(ctx, true, new HttpVersion[]{ HttpVersion.HTTP_2_0 }, HTTP2_ONLY, 200);
   }

   @Test
   public void testAlpnForceHttp2ServerKeep(TestContext ctx) {
      test(ctx, true, new HttpVersion[]{ HttpVersion.HTTP_2_0 }, HTTP1x_ONLY, 500);
   }

   @Test
   public void testAlpnForceHttp1x(TestContext ctx) {
      test(ctx, true, new HttpVersion[]{ HttpVersion.HTTP_1_1 }, HTTP2_ONLY, 500);
   }

   @Test
   public void testH2cUpgrade(TestContext ctx) {
      test(ctx, false, new HttpVersion[]{ HttpVersion.HTTP_2_0 }, HTTP2_ONLY, 200);
   }

   @Test
   public void testCleartextDefault(TestContext ctx) {
      test(ctx, false, HttpVersion.ALL_VERSIONS, HTTP2_ONLY, 500);
   }

   @Test
   public void testCleartextDefaultServer1x(TestContext ctx) {
      test(ctx, false, HttpVersion.ALL_VERSIONS, HTTP1x_ONLY, 500);
   }

   @Test
   public void testCleartextForceHttp1x(TestContext ctx) {
      test(ctx, false, new HttpVersion[]{ HttpVersion.HTTP_1_1 }, HTTP2_ONLY, 500);
   }

   private void test(TestContext ctx, boolean ssl, HttpVersion[] clientVersions, List<io.vertx.core.http.HttpVersion> serverVersions, int expectedStatus) {
      test(ctx, ssl, clientVersions, serverVersions, HttpVersionsTest::requireHttp2,
            (client, async) -> sendRequestAndAssertStatus(ctx, client, async, HttpMethod.GET, "/ping", expectedStatus));
   }

   private static void requireHttp2(HttpServerRequest req) {
      if (req.version() != io.vertx.core.http.HttpVersion.HTTP_2) {
         req.response().setStatusCode(500).end("HTTP/2 required.");
      } else {
         req.response().setStatusCode(200).end("Hello");
      }
   }
}
