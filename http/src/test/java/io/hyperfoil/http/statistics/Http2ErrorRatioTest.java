package io.hyperfoil.http.statistics;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.http.config.HttpBuilder;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
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
   protected Action validateConnection(VertxTestContext ctx) {
      return session -> {
         ctx.verify(() -> {
            assertSame(((HttpConnection) session.currentRequest().connection()).version(), HttpVersion.HTTP_2_0);
            ctx.completeNow();
         });
      };
   }
}
