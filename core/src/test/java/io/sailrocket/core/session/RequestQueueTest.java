package io.sailrocket.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.Request;

public class RequestQueueTest {
   private static final io.sailrocket.api.connection.Request DUMMY = new Request() {
      @Override
      public Connection connection() {
         return null;
      }
   };

   @Test
   public void testSequential() {
      RequestQueue queue = new RequestQueueImpl(null, 16);
      int consumeIndex = 0;
      for (int publishIndex = 0; publishIndex < 1000; ) {
         if (ThreadLocalRandom.current().nextBoolean()) {
            RequestQueue.Request request = queue.prepare(DUMMY);
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
         RequestQueue.Request request = queue.prepare(DUMMY);
         assertThat(request).isNotNull();
      }
      for (int i = 0; i < 16; ++i) {
         queue.complete();
      }
      // shift offset
      queue.prepare(DUMMY);
      queue.complete();
      for (int i = 0; i < 16; ++i) {
         RequestQueue.Request request = queue.prepare(DUMMY);
         assertThat(request).isNotNull();
      }
      for (int i = 0; i < 16; ++i) {
         queue.complete();
      }
   }
}
