package io.sailrocket.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import io.sailrocket.api.collection.RequestQueue;

public class RequestQueueTest {
   @Test
   public void testSequential() {
      RequestQueue queue = new RequestQueueImpl(null, 16);
      int consumeIndex = 0;
      for (int publishIndex = 0; publishIndex < 1000; ) {
         if (ThreadLocalRandom.current().nextBoolean()) {
            RequestQueue.Request request = queue.prepare();
            if (request == null) {
               continue;
            }
            request.startTime = publishIndex++;
         } else {
            if (queue.isFull()) {
               continue;
            }
            RequestQueue.Request request = queue.complete();
            assertThat(request.startTime).isEqualTo(consumeIndex);
            consumeIndex++;
         }
      }
   }

   @Test
   public void testCapacity() {
      RequestQueue queue = new RequestQueueImpl(null, 16);
      for (int i = 0; i < 16; ++i) {
         RequestQueue.Request request = queue.prepare();
         assertThat(request).isNotNull();
      }
      for (int i = 0; i < 16; ++i) {
         queue.complete();
      }
      // shift offset
      queue.prepare();
      queue.complete();
      for (int i = 0; i < 16; ++i) {
         RequestQueue.Request request = queue.prepare();
         assertThat(request).isNotNull();
      }
      for (int i = 0; i < 16; ++i) {
         queue.complete();
      }
   }
}
