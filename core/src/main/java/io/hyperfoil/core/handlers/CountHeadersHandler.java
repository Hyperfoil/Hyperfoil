package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.statistics.IntValue;
import io.hyperfoil.function.SerializableSupplier;

public class CountHeadersHandler implements HeaderHandler {
   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      IntValue custom = request.statistics().getCustom(request.startTimestampMillis(), header, IntValue::new);
      custom.add(1);
   }

   /**
    * Stores number of occurences of each header in custom statistics (these can be displayed in CLI using the <code>stats -c</code> command).
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("countHeaders")
   public static class Builder implements HeaderHandler.Builder {
      @Override
      public CountHeadersHandler build(SerializableSupplier<? extends Step> step) {
         return new CountHeadersHandler();
      }
   }
}
