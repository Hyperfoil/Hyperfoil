package io.hyperfoil.http.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.api.HttpCache;

public class ClearHttpCacheAction implements Action {
   @Override
   public void run(Session session) {
      HttpCache httpCache = HttpCache.get(session);
      if (httpCache != null) {
         httpCache.clear();
      }
   }

   /**
    * Drops all entries from HTTP cache in the session.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("clearHttpCache")
   public static class Builder implements Action.Builder {
      @Override
      public ClearHttpCacheAction build() {
         return new ClearHttpCacheAction();
      }
   }
}
