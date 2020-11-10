package io.hyperfoil.core.handlers.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

public class MultiplexStatusHandler extends BaseRangeStatusHandler implements ResourceUtilizer {
   private final StatusHandler[][] handlers;
   private final StatusHandler[] other;

   public MultiplexStatusHandler(int[] ranges, StatusHandler[][] handlers, StatusHandler[] other) {
      super(ranges);
      this.handlers = handlers;
      this.other = other;
   }

   @Override
   protected void onStatusRange(HttpRequest request, int status, int index) {
      for (StatusHandler h : handlers[index]) {
         h.handleStatus(request, status);
      }
   }

   @Override
   protected void onOtherStatus(HttpRequest request, int status) {
      if (other != null) {
         for (StatusHandler h : other) {
            h.handleStatus(request, status);
         }
      }
   }

   @Override
   public void reserve(Session session) {
      for (StatusHandler[] hs : handlers) {
         ResourceUtilizer.reserve(session, (Object[]) hs);
      }
      ResourceUtilizer.reserve(session, (Object[]) other);
   }

   /**
    * Multiplexes the status based on range into different status handlers.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("multiplex")
   public static class Builder implements StatusHandler.Builder, PartialBuilder {
      private final Map<String, List<StatusHandler.Builder>> handlers = new HashMap<>();

      /**
       * Run another handler if the range matches.
       *
       * @param range Possible values of the status separated by commas (,). Ranges can be set using low-high (inclusive) (e.g. 200-299), or replacing lower digits with 'x' (e.g. 2xx).
       * @return Builder
       */
      @Override
      public ServiceLoadedBuilderProvider<StatusHandler.Builder> withKey(String range) {
         List<StatusHandler.Builder> handlers = new ArrayList<>();
         add(range, handlers);
         return new ServiceLoadedBuilderProvider<>(StatusHandler.Builder.class, handlers::add);
      }

      public Builder add(String range, List<StatusHandler.Builder> handlers) {
         if (this.handlers.putIfAbsent(range, handlers) != null) {
            throw new BenchmarkDefinitionException("Range '" + range + "' is already set.");
         }
         return this;
      }

      @Override
      public MultiplexStatusHandler build() {
         List<Integer> ranges = new ArrayList<>();
         List<StatusHandler[]> handlers = new ArrayList<>();
         StatusHandler[] other = checkAndSortRanges(this.handlers, ranges, handlers, list -> list.stream().map(StatusHandler.Builder::build).toArray(StatusHandler[]::new));
         return new MultiplexStatusHandler(ranges.stream().mapToInt(Integer::intValue).toArray(), handlers.toArray(new StatusHandler[0][]), other);
      }
   }
}
