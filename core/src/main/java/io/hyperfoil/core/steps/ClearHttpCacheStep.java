package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;

public class ClearHttpCacheStep implements Action.Step {
   @Override
   public void run(Session session) {
      session.httpCache().clear();
   }

   /**
    * Drops all entries from HTTP cache in the session.
    */
   public static class Builder implements Action.Builder {
      @Override
      public ClearHttpCacheStep build() {
         return new ClearHttpCacheStep();
      }
   }

   @MetaInfServices(Action.BuilderFactory.class)
   public static class BuilderFactory implements Action.BuilderFactory {
      @Override
      public String name() {
         return "clearHttpCache";
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
