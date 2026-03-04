package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.netty.buffer.ByteBuf;

public class ThrottledSessionsTest extends BaseHttpScenarioTest {

   private static final int RATE = 10;
   private static final long DURATION_MS = 2000;
   // Server delay longer than inter-arrival time (100ms) guarantees the single
   // session is still waiting for the response when the next fire time arrives,
   // forcing throttling. Unlike sleeping in onRequest (which blocks the event
   // loop and prevents proceed() from firing), a server-side delay keeps the
   // client event loop free to schedule proceed() and discover an empty pool.
   private static final long SERVER_DELAY_MS = 150;

   @Override
   protected void initRouter() {
      router.get("/slow").handler(ctx -> {
         ctx.vertx().setTimer(SERVER_DELAY_MS, id -> ctx.response().setStatusCode(200).end());
      });
   }

   @Override
   protected int threads() {
      return 1;
   }

   @Test
   public void testThrottledSessionsWithCompensation() {
      benchmarkBuilder.plugin(HttpPluginBuilder.class).ergonomics()
            .compensateInternalLatency(true);

      AtomicInteger requestCount = new AtomicInteger();

      // @formatter:off
      benchmarkBuilder.addPhase("test")
            .constantRate(RATE)
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(1)
            .scenario()
            .initialSequence("request")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/slow")
                  .handler()
                     .rawBytes(new RawBytesHandler() {
                        @Override
                        public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                           requestCount.incrementAndGet();
                        }

                        @Override
                        public void onResponse(Request request, ByteBuf buf, int offset, int length,
                              boolean isLastPart) {
                        }
                     })
                  .endHandler()
                  .endStep()
            .endSequence();
      // @formatter:on

      runScenario();

      // With maxSessions(1) and server delay > inter-arrival time, most fire
      // times find the pool empty and increment throttledUsers. Sessions are
      // reused via notifyFinished() throttle path.
      // Without the fix, this throws IllegalStateException.
      assertThat(requestCount.get()).isGreaterThan(1);
   }

   @Test
   public void testThrottledSessionsWithoutCompensation() {
      AtomicInteger requestCount = new AtomicInteger();

      // @formatter:off
      benchmarkBuilder.addPhase("test")
            .constantRate(RATE)
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(1)
            .scenario()
            .initialSequence("request")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/slow")
                  .handler()
                     .rawBytes(new RawBytesHandler() {
                        @Override
                        public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                           requestCount.incrementAndGet();
                        }

                        @Override
                        public void onResponse(Request request, ByteBuf buf, int offset, int length,
                              boolean isLastPart) {
                        }
                     })
                  .endHandler()
                  .endStep()
            .endSequence();
      // @formatter:on

      runScenario();

      assertThat(requestCount.get()).isGreaterThan(1);
   }
}
