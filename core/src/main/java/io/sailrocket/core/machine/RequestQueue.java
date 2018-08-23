package io.sailrocket.core.machine;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class RequestQueue {
   private static final Logger log = LoggerFactory.getLogger(RequestQueue.class);

   private final Request[] queue;
   private final int mask;
   private int readerIndex = 0;
   private int writerIndex = 0;

   RequestQueue(int maxRequests) {
      int shift = 32 - Integer.numberOfLeadingZeros(maxRequests - 1);
      mask = (1 << shift) - 1;
      queue = new Request[mask + 1];
      for (int i = 0; i < queue.length; ++i) {
         queue[i] = new Request();
      }
   }

   /**
    * @return Request slot or <code>null</code> if none available
    */
   Request prepare() {
      log.trace("Prepare: {}/{}", writerIndex, readerIndex);
      // This algorithm allows writerIndex > mask
      if (writerIndex == readerIndex - 1 || writerIndex == readerIndex + mask + 1) {
         return null;
      }
      Request slot = queue[writerIndex & mask];
      if (writerIndex > mask) {
         writerIndex = 0;
      } else {
         writerIndex++;
      }
      return slot;
   }

   Request peek() {
      return queue[readerIndex & mask];
   }

   Request complete() {
      log.trace("Complete: {}/{}", writerIndex, readerIndex);
      assert readerIndex != writerIndex;
      Request slot = queue[readerIndex & mask];
      if (readerIndex > mask) {
         readerIndex = 0;
      } else {
         readerIndex++;
      }
      return slot;
   }

   static class Request {
      long startTime;
      SequenceInstance sequence;
   }
}
