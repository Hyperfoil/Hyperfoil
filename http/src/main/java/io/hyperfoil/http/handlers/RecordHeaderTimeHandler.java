package io.hyperfoil.http.handlers;

import java.util.concurrent.TimeUnit;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.function.SerializableToLongFunction;
import io.netty.util.AsciiString;

public class RecordHeaderTimeHandler implements HeaderHandler {
   private final int stepId;
   private final String header;
   private final String statistics;
   private final SerializableToLongFunction<CharSequence> transform;
   private transient AsciiString asciiHeader;

   public RecordHeaderTimeHandler(int stepId, String header, String statistics, SerializableToLongFunction<CharSequence> transform) {
      this.stepId = stepId;
      this.header = header;
      this.statistics = statistics;
      this.transform = transform;
      this.asciiHeader = new AsciiString(header);
   }

   private Object readResolve() {
      this.asciiHeader = new AsciiString(header);
      return this;
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (!asciiHeader.contentEqualsIgnoreCase(header)) {
         return;
      }
      long longValue = transform.applyAsLong(value);
      if (longValue < 0) {
         // we're not recording negative values
         return;
      }
      Statistics statistics = request.session.statistics(stepId, this.statistics);
      // we need to set both requests and responses to calculate stats properly
      statistics.incrementRequests(request.startTimestampMillis());
      statistics.recordResponse(request.startTimestampMillis(), longValue);
   }

   /**
    * Records alternative metric based on values from a header (e.g. when a proxy reports processing time).
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("recordHeaderTime")
   public static class Builder implements HeaderHandler.Builder, InitFromParam<Builder> {
      private String header;
      private String metric;
      private String unit;

      @Override
      public Builder init(String param) {
         header = param;
         return this;
      }

      @Override
      public RecordHeaderTimeHandler build() {
         if (header == null || header.isEmpty()) {
            throw new BenchmarkDefinitionException("Must define the header.");
         } else if (header.chars().anyMatch(c -> c > 0xFF)) {
            throw new BenchmarkDefinitionException("Header contains non-ASCII characters.");
         }
         if (metric == null) {
            metric = header;
         }
         SerializableToLongFunction<CharSequence> transform = io.hyperfoil.core.util.Util::parseLong;
         if (unit != null) {
            switch (unit) {
               case "ms":
                  transform = value -> TimeUnit.MILLISECONDS.toNanos(Util.parseLong(value));
                  break;
               case "ns":
                  break;
               default:
                  throw new BenchmarkDefinitionException("Unknown unit '" + unit + "'");
            }
         }

         return new RecordHeaderTimeHandler(Locator.current().step().id(), header, metric, transform);
      }

      /**
       * Header carrying the time.
       *
       * @param header Header name.
       * @return Self.
       */
      public Builder header(String header) {
         this.header = header;
         return this;
      }

      /**
       * Name of the created metric.
       *
       * @param metric Metric name.
       * @return Self.
       */
      public Builder metric(String metric) {
         this.metric = metric;
         return this;
      }

      /**
       * Time unit in the header; use either `ms` or `ns`.
       *
       * @param unit Ms or ns.
       * @return Self.
       */
      public Builder unit(String unit) {
         this.unit = unit;
         return this;
      }
   }
}
