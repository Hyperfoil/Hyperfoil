package io.sailrocket.core.session;

import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class RequestQueueImpl implements RequestQueue {
   private static final Logger log = LoggerFactory.getLogger(RequestQueueImpl.class);

   private final Session session;
   private final Request[] queue;
   private final int mask;
   private int freeSlots;
   private int readerIndex = 0;
   private int writerIndex = 0;

   RequestQueueImpl(Session session, int maxRequests) {
      this.session = session;
      int shift = 32 - Integer.numberOfLeadingZeros(maxRequests - 1);
      mask = (1 << shift) - 1;
      queue = new Request[mask + 1];
      freeSlots = queue.length;
      for (int i = 0; i < queue.length; ++i) {
         queue[i] = new Request(this);
      }
   }

   /**
    * @return Request slot or <code>null</code> if none available
    * @param request
    */
   @Override
   public Request prepare(io.sailrocket.api.connection.Request request) {
      if (freeSlots == 0) {
         return null;
      }
      Request slot = queue[writerIndex];
      writerIndex = (writerIndex + 1) & mask;
      freeSlots--;
      slot.request = request;
      return slot;
   }

   @Override
   public Request peek() {
      return queue[readerIndex & mask];
   }

   @Override
   public Request complete() {
      // This method is called only upon successful completion. Failed (timed out) requests are completed
      // out of band by setting Request.failed = true and it's up to the next successful request or
      // RequestQueue.gc() to clean them up.
      Request slot;
      for (;;) {
         assert freeSlots != queue.length;
         slot = queue[readerIndex];
         readerIndex = (readerIndex + 1) & mask;
         freeSlots++;
         // If the request is already complete it is not the one we're completing...
         if (slot.request != null) {
            return slot;
         }
      }
   }

   @Override
   public boolean isDepleted() {
      return freeSlots == 0;
   }

   @Override
   public boolean isFull() {
      gc();
      return freeSlots == queue.length;
   }

   @Override
   public void gc() {
      while (freeSlots < queue.length && queue[readerIndex].request == null) {
         readerIndex = (readerIndex + 1) & mask;
         ++freeSlots;
      }
   }

   @Override
   public Session session() {
      return session;
   }
}
