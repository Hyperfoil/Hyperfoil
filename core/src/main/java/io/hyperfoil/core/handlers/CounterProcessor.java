package io.hyperfoil.core.handlers;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.netty.buffer.ByteBuf;

public class CounterProcessor implements RawBytesHandler {

   private static final Logger log = LogManager.getLogger(CounterProcessor.class);

   private final AtomicInteger requestCounter = new AtomicInteger(0);
   private final AtomicInteger responseCounter = new AtomicInteger(0);
   private final int max;

   public CounterProcessor() {
      this(1000);
   }

   public CounterProcessor(int max) {
      this.max = max;
   }

   @Override
   public void onRequest(Request request, ByteBuf buf, int offset, int length) {
      int c = requestCounter.incrementAndGet();
      if (c == max) {
         log.debug("Sent {} requests", c);
         requestCounter.set(0);
      }
   }

   @Override
   public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
      int c = responseCounter.incrementAndGet();
      if (c == max) {
         log.debug("Received {} responses", c);
         responseCounter.set(0);
      }
   }
}
