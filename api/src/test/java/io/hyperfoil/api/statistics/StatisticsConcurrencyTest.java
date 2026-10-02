package io.hyperfoil.api.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.hyperfoil.api.config.StartTimeSource;
import io.hyperfoil.api.session.Session;

public class StatisticsConcurrencyTest {
   private static final long BASE_TIME = 1710774000000L;

   @Test
   @Timeout(30)
   public void concurrentCollectionPreservesBackdatedRequestsAndResponses() throws Exception {
      int buckets = 32;
      int requests = 200_000;
      int pending = 64;
      var sources = new Timestamp[buckets];
      for (int i = 0; i < buckets; ++i) {
         sources[i] = new Timestamp(BASE_TIME + i * 1000L);
      }
      var statistics = new Statistics(BASE_TIME);
      long[] collectedRequests = new long[buckets];
      long[] collectedResponses = new long[buckets];
      long[] collectedSamples = new long[buckets];
      Consumer<StatisticsSnapshot> collect = snapshot -> {
         collectedRequests[snapshot.sequenceId] += snapshot.requestCount;
         collectedResponses[snapshot.sequenceId] += snapshot.responseCount;
         collectedSamples[snapshot.sequenceId] += snapshot.histogram.getTotalCount();
      };
      var start = new CountDownLatch(1);
      try (var executor = Executors.newSingleThreadExecutor()) {
         var writer = executor.submit(() -> {
            start.await();
            for (int i = 0; i < requests; ++i) {
               // Revisit older buckets while the reader switches buffers; also cross
               // the initial array capacity to exercise resizing during collection.
               statistics.incrementRequests(sources[i % buckets], null);
               if (i >= pending) {
                  statistics.recordResponse(sources[(i - pending) % buckets], 100_000, null);
               }
            }
            for (int i = requests - pending; i < requests; ++i) {
               statistics.recordResponse(sources[i % buckets], 100_000, null);
            }
            statistics.end(BASE_TIME + buckets * 1000L);
            return null;
         });
         start.countDown();
         do {
            statistics.visitSnapshots(collect);
            LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(50));
         } while (!writer.isDone());
         writer.get(10, TimeUnit.SECONDS);
         statistics.visitSnapshots(collect);
         // Repeated collection must not duplicate anything after completion.
         statistics.visitSnapshots(collect);
      }
      for (int i = 0; i < buckets; ++i) {
         assertEquals(requests / buckets, collectedRequests[i], "Requests in bucket " + i);
         assertEquals(requests / buckets, collectedResponses[i], "Responses in bucket " + i);
         assertEquals(requests / buckets, collectedSamples[i], "Histogram samples in bucket " + i);
      }
   }

   private record Timestamp(long millis) implements StartTimeSource {
      @Override
      public long getStartTimestampMillis(Session session) {
         return millis;
      }

      @Override
      public long getStartTimestampNanos(Session session) {
         return TimeUnit.MILLISECONDS.toNanos(millis);
      }
   }
}
