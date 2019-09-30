package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
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
   @MetaInfServices(Action.Builder.class)
   @Name("clearHttpCache")
   public static class Builder implements Action.Builder {
      @Override
      public ClearHttpCacheStep build() {
         return new ClearHttpCacheStep();
      }
   }
}
