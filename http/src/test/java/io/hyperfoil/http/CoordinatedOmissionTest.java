package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.netty.buffer.ByteBuf;

public class CoordinatedOmissionTest extends BaseHttpScenarioTest {

   private static final int RATE = 10; // 10 req/s
   private static final long DURATION_MS = 3000;
   // Sleep longer than the inter-request spacing (100ms) to block the event loop,
   // delaying subsequent session starts. With the CO fix, startTimestampNanos
   // comes from the rate generator's fire time — unaffected by the delay.
   // Without the fix, System.nanoTime() at the delayed Request.start() would
   // produce inflated diffs, exposing internal coordinated omission.
   private static final long SLEEP_MS = 150;
   private static final long EXPECTED_SPACING_NS = 1_000_000_000L / RATE; // 100ms

   @Override
   protected void initRouter() {
      router.get("/ping").handler(ctx -> ctx.response().setStatusCode(200).end());
   }

   @Override
   protected int threads() {
      return 1;
   }

   private long[] runWithCoordinatedOmission(boolean enabled) {
      benchmarkBuilder.plugin(HttpPluginBuilder.class).ergonomics()
            .compensateInternalLatency(enabled);

      LongList capturedStartNanos = new LongList(RATE * 5);

      benchmarkBuilder.addPhase("test")
            .constantRate(RATE)
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(RATE * 5)
            .scenario()
            .initialSequence("request")
            .step(SC).httpRequest(HttpMethod.GET)
            .path("/ping")
            .handler()
            .rawBytes(new RawBytesHandler() {
               @Override
               public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                  capturedStartNanos.add(request.startTimestampNanos());
                  try {
                     Thread.sleep(SLEEP_MS);
                  } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                  }
               }

               @Override
               public void onResponse(Request request, ByteBuf buf, int offset, int length,
                     boolean isLastPart) {
               }
            })
            .endHandler()
            .endStep()
            .endSequence();

      runScenario();

      return capturedStartNanos.toArray();
   }

   @Test
   public void testWithFixStartTimestampsMatchIntendedFireTimes() {
      long[] captured = runWithCoordinatedOmission(true);

      assertThat(captured.length).isGreaterThanOrEqualTo(RATE);

      for (int i = 1; i < captured.length; i++) {
         long diff = captured[i] - captured[i - 1];
         assertThat(diff)
               .describedAs("diff[%d] between request %d and %d", i - 1, i - 1, i)
               .isEqualTo(EXPECTED_SPACING_NS);
      }
   }

   @Test
   public void testWithoutFixStartTimestampsInflatedByDelay() {
      long[] captured = runWithCoordinatedOmission(false);

      assertThat(captured.length).isGreaterThanOrEqualTo(RATE);

      // Without the CO fix, Request.start() uses System.nanoTime(). The sleep
      // blocks the event loop beyond the intended spacing, so at least one
      // consecutive diff must be >= the sleep duration.
      long maxDiff = 0;
      for (int i = 1; i < captured.length; i++) {
         long diff = captured[i] - captured[i - 1];
         if (diff > maxDiff) {
            maxDiff = diff;
         }
      }
      assertThat(maxDiff)
            .describedAs("Without CO fix, max diff should be inflated by the sleep")
            .isGreaterThanOrEqualTo(SLEEP_MS * 1_000_000L);
   }

   private static class LongList {
      private long[] data;
      private int size;

      LongList(int initialCapacity) {
         data = new long[initialCapacity];
      }

      void add(long value) {
         if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
         }
         data[size++] = value;
      }

      long[] toArray() {
         return Arrays.copyOf(data, size);
      }
   }
}
