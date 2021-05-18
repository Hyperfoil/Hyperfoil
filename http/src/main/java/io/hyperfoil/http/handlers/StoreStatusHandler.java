package io.hyperfoil.http.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.core.session.SessionFactory;

public class StoreStatusHandler implements StatusHandler {
   private final IntAccess toVar;

   public StoreStatusHandler(IntAccess toVar) {
      this.toVar = toVar;
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      toVar.setInt(request.session, status);
   }

   /**
    * Stores the status into session variable.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("store")
   public static class Builder implements StatusHandler.Builder, InitFromParam<Builder> {
      private Object toVar;

      /**
       * @param param Variable name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
      }

      /**
       * Variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(Object toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public StoreStatusHandler build() {
         return new StoreStatusHandler(SessionFactory.intAccess(toVar));
      }
   }
}
