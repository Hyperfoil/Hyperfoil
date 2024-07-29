package io.hyperfoil.http.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.statistics.Counters;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.http.api.HttpRequest;

public class CountHeadersHandler implements HeaderHandler {
   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      request.statistics().update("countHeaders", request.startTimestampMillis(),
            Counters::new, Counters::increment, header);
   }

   /**
    * Stores number of occurences of each header in custom statistics (these can be displayed in CLI using the
    * <code>stats -c</code> command).
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("countHeaders")
   public static class Builder implements HeaderHandler.Builder {
      @Override
      public CountHeadersHandler build() {
         return new CountHeadersHandler();
      }
   }
}
