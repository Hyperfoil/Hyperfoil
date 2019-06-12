package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.statistics.IntValue;
import io.hyperfoil.function.SerializableSupplier;

public class CountHeadersHandler implements HeaderHandler {
   @Override
   public void handleHeader(Request request, CharSequence header, CharSequence value) {
      IntValue custom = request.statistics().getCustom(request.startTimestampMillis(), header, IntValue::new);
      custom.add(1);
   }

   public static class Builder implements HeaderHandler.Builder {
      @Override
      public CountHeadersHandler build(SerializableSupplier<? extends Step> step) {
         return new CountHeadersHandler();
      }
   }

   @MetaInfServices(HeaderHandler.BuilderFactory.class)
   public static class BuilderFactory implements HeaderHandler.BuilderFactory {
      @Override
      public String name() {
         return "countHeaders";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder();
      }
   }
}
