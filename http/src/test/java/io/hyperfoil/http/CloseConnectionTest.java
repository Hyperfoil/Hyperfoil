package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.handlers.CloseConnectionHandler;
import io.hyperfoil.http.api.HttpMethod;
import io.netty.buffer.ByteBuf;

public class CloseConnectionTest extends BaseHttpScenarioTest {
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
                     .body(new CloseConnectionHandler.Builder())
                     .body(f -> new TestProcessor(closed))
                  .endHandler()
               .endStep();
      // @formatter:on
      runScenario();
      assertThat(closed.get()).isTrue();
   }

   @Test
   public void testWithBody() {
      test("/body");
   }

   @Test
   public void testWithoutBody() {
      test("/nobody");
   }

   private static class TestProcessor implements Processor {
      private final AtomicBoolean closed;

      private TestProcessor(AtomicBoolean closed) {
         this.closed = closed;
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         // ignore
      }

      @Override
      public void after(Session session) {
         closed.set(session.currentRequest().connection().isClosed());
      }
   }
}
