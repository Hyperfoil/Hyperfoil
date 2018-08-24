package io.sailrocket.core.session;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class RequestQueueImpl implements io.sailrocket.api.RequestQueue {
   private static final Logger log = LoggerFactory.getLogger(RequestQueueImpl.class);

   private final Request[] queue;
   private final int mask;
   private int readerIndex = 0;
   private int writerIndex = 0;

   RequestQueueImpl(int maxRequests) {
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
   @Override
   public Request prepare() {
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

   @Override
   public Request peek() {
      return queue[readerIndex & mask];
   }

   @Override
   public Request complete() {
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

}
