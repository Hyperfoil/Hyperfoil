package io.hyperfoil.http.statistics;

import org.junit.runner.RunWith;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.http.config.HttpBuilder;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class Http2ErrorRatioTest extends ErrorRatioTest {
   @Override
   protected boolean useHttps() {
      return true;
   }

   @Override
   protected void initHttp(HttpBuilder http) {
      http.allowHttp1x(false);
   }

   @Override
   protected Action validateConnection(TestContext ctx) {
      return session -> ctx
            .assertTrue(((HttpConnection) session.currentRequest().connection()).version() == HttpVersion.HTTP_2_0);
   }
}
