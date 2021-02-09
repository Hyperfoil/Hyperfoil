package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.statistics.LongValue;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.buffer.ByteBuf;

public class TransferSizeRecorder implements RawBytesHandler {
   private final String requestMetric;
   private final String responseMetric;

   public TransferSizeRecorder(String requestMetric, String responseMetric) {
      this.requestMetric = requestMetric;
      this.responseMetric = responseMetric;
   }

   @Override
   public void onRequest(Request request, ByteBuf buf, int offset, int length) {
      Statistics statistics = request.statistics();
      statistics.getCustom(request.startTimestampMillis(), requestMetric, LongValue::new).add(length);
   }

   @Override
   public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
      Statistics statistics = request.statistics();
      statistics.getCustom(request.startTimestampMillis(), responseMetric, LongValue::new).add(length);
   }

   /**
    * Accumulates request and response sizes into custom metrics.
    */
   @MetaInfServices(RawBytesHandler.Builder.class)
   @Name("transferSizeRecorder")
   public static class Builder implements RawBytesHandler.Builder {
      private String requestMetric;
      private String responseMetric;

      /**
       * Name of the custom metric for collecting sent request bytes.
       *
       * @param requestMetric Name of the custom metric.
       * @return Self.
       */
      public Builder requestMetric(String requestMetric) {
         this.requestMetric = requestMetric;
         return this;
      }


      /**
       * Name of the custom metric for collecting response bytes.
       *
       * @param responseMetric Name of the custom metric.
       * @return Self.
       */
      public Builder responseMetric(String responseMetric) {
         this.responseMetric = responseMetric;
         return this;
      }

      @Override
      public TransferSizeRecorder build() {
         return new TransferSizeRecorder(requestMetric, responseMetric);
      }
   }
}
