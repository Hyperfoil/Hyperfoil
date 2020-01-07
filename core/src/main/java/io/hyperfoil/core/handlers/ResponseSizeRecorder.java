package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.statistics.LongValue;
import io.hyperfoil.api.statistics.Statistics;

public class ResponseSizeRecorder implements RawBytesHandler {
   private final String customMetric;

   public ResponseSizeRecorder(String customMetric) {
      this.customMetric = customMetric;
   }

   @Override
   public void accept(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
      Statistics statistics = request.statistics();
      statistics.getCustom(request.startTimestampMillis(), customMetric, LongValue::new).add(length);
   }

   /**
    * Accumulates response sizes into custom metric.
    */
   @MetaInfServices(RawBytesHandler.Builder.class)
   @Name("responseSizeRecorder")
   public static class Builder implements RawBytesHandler.Builder, InitFromParam<Builder> {
      private String customMetric;

      /**
       * @param param Name of the custom metric.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return customMetric(param);
      }

      /**
       * Name of the custom metric.
       *
       * @param customMetric Name of the custom metric.
       * @return Self.
       */
      public Builder customMetric(String customMetric) {
         this.customMetric = customMetric;
         return this;
      }

      @Override
      public ResponseSizeRecorder build() {
         return new ResponseSizeRecorder(customMetric);
      }
   }
}
