package io.hyperfoil.core.handlers;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.core.http.CloseConnectionHandler;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.netty.buffer.ByteBuf;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CloseConnectionTest extends BaseScenarioTest {
   @Override
   protected void initRouter() {
      router.get("/body").handler(ctx -> {
         ctx.response().end("Hello");
      });
      router.get("/nobody").handler(ctx -> {
         ctx.response().end();
      });
   }

   private void test(String path) {
      AtomicBoolean closed = new AtomicBoolean(false);
      // @formatter:off
      scenario()
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.POST)
                  .path(path)
                  .handler()
                     .body(new HttpRequestProcessorBuilder.RequestProcessorAdapter(new CloseConnectionHandler()))
                     .body(new TestProcessor(closed))
                  .endHandler()
               .endStep();
      // @formatter:on
      runScenario();
      assertThat(closed.get()).isTrue();
   }

   @Test
   public void testWithBody(TestContext ctx) {
      test("/body");
   }

   @Test
   public void testWithoutBody(TestContext ctx) {
      test("/nobody");
   }

   private static class TestProcessor implements Processor<HttpRequest> {
      private final AtomicBoolean closed;

      private TestProcessor(AtomicBoolean closed) {
         this.closed = closed;
      }

      @Override
      public void process(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
         // ignore
      }

      @Override
      public void after(HttpRequest request) {
         closed.set(request.connection().isClosed());
      }
   }
}
