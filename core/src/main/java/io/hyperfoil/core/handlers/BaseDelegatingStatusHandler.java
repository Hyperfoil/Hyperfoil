package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;

public abstract class BaseDelegatingStatusHandler implements StatusHandler, ResourceUtilizer  {
   protected final StatusHandler[] handlers;

   public BaseDelegatingStatusHandler(StatusHandler[] handlers) {
      this.handlers = handlers;
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      for (StatusHandler handler : handlers) {
         handler.handleStatus(request, status);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) handlers);
   }

   public abstract static class Builder<S extends Builder<S>> implements StatusHandler.Builder {
      private final List<StatusHandler.Builder> handlers = new ArrayList<>();

      @SuppressWarnings("unchecked")
      public S handlers(Collection<? extends StatusHandler.Builder> handlers) {
         this.handlers.addAll(handlers);
         return (S) this;
      }

      @Override
      public void prepareBuild() {
         handlers.forEach(StatusHandler.Builder::prepareBuild);
      }

      protected StatusHandler[] buildHandlers() {
         return handlers.stream().map(StatusHandler.Builder::build).toArray(StatusHandler[]::new);
      }
   }
}

