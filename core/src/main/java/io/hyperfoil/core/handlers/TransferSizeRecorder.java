package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatsExtension;
import io.netty.buffer.ByteBuf;

public class TransferSizeRecorder implements RawBytesHandler {
   private final String key;

   public TransferSizeRecorder(String key) {
      this.key = key;
   }

   @Override
   public void onRequest(Request request, ByteBuf buf, int offset, int length) {
      Statistics statistics = request.statistics();
      statistics.update(key, request.startTimestampMillis(), Stats::new, (s, l) -> s.sent += l, length);
   }

   @Override
   public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
      Statistics statistics = request.statistics();
      statistics.update(key, request.startTimestampMillis(), Stats::new, (s, l) -> s.received += l, length);
   }

   /**
    * Accumulates request and response sizes into custom metrics.
    */
   @MetaInfServices(RawBytesHandler.Builder.class)
   @Name("transferSizeRecorder")
   public static class Builder implements RawBytesHandler.Builder {
      private String key;

      /**
       * Name of the custom metric for collecting request/response bytes.
       *
       * @param metric Name of the custom metric.
       * @return Self.
       */
      public Builder key(String metric) {
         this.key = metric;
         return this;
      }

      @Override
      public TransferSizeRecorder build() {
         return new TransferSizeRecorder(key);
      }
   }

   @MetaInfServices(StatsExtension.class)
   @JsonTypeName("transfersize")
   public static class Stats implements StatsExtension {
      private static final String[] HEADERS = { "sent", "received" };
      public long sent;
      public long received;

      @Override
      public boolean isNull() {
         return sent + received == 0;
      }

      @Override
      public void add(StatsExtension other) {
         if (other instanceof Stats) {
            Stats o = (Stats) other;
            sent += o.sent;
            received += o.received;
         } else {
            throw new IllegalArgumentException(other.toString());
         }
      }

      @Override
      public void subtract(StatsExtension other) {
         if (other instanceof Stats) {
            Stats o = (Stats) other;
            sent -= o.sent;
            received -= o.received;
         } else {
            throw new IllegalArgumentException(other.toString());
         }
      }

      @Override
      public void reset() {
         sent = 0;
         received = 0;
      }

      @Override
      public StatsExtension clone() {
         Stats copy = new Stats();
         copy.sent = sent;
         copy.received = received;
         return copy;
      }

      @Override
      public String[] headers() {
         return HEADERS;
      }

      @Override
      public String byHeader(String header) {
         switch (header) {
            case "sent":
               return String.valueOf(sent);
            case "received":
               return String.valueOf(received);
            default:
               return "<unknown header: " + header + ">";
         }
      }
   }
}
