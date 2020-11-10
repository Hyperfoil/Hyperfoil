package io.hyperfoil.core.handlers.http;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class StoreStatusHandler implements StatusHandler, ResourceUtilizer {
   private final Access toVar;

   public StoreStatusHandler(Access toVar) {
      this.toVar = toVar;
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      toVar.setInt(request.session, status);
   }

   @Override
   public void reserve(Session session) {
      toVar.declareInt(session);
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
         return new StoreStatusHandler(SessionFactory.access(toVar));
      }
   }
}
