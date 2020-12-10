package io.hyperfoil.core.handlers.http;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.statistics.IntValue;

public class StatusToStatsHandler implements StatusHandler {
   private static final int FIRST_STATUS = 100;
   private static final int LAST_STATUS = 599;
   private static final String[] statusStrings;

   static {
      statusStrings = new String[LAST_STATUS - FIRST_STATUS + 1];
      for (int i = 0; i <= LAST_STATUS - FIRST_STATUS; ++i) {
         statusStrings[i] = "status_" + (i + FIRST_STATUS);
      }
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      String statusString;
      if (status >= FIRST_STATUS && status <= LAST_STATUS) {
         statusString = statusStrings[status - FIRST_STATUS];
      } else {
         statusString = "status_" + status;
      }
      IntValue custom = request.statistics().getCustom(request.startTimestampMillis(), statusString, IntValue::new);
      custom.add(1);
   }

   /**
    * Records number of occurrences of each status counts into custom statistics
    * (these can be displayed in CLI using <code>stats -c</code>).
    */
   @MetaInfServices
   @Name("stats")
   public static class Builder implements StatusHandler.Builder {
      @Override
      public StatusHandler build() {
         return new StatusToStatsHandler();
      }
   }
}
