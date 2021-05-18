package io.hyperfoil.http.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.StatusHandler;

public abstract class BaseDelegatingStatusHandler implements StatusHandler {
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

   public abstract static class Builder<S extends Builder<S>> implements StatusHandler.Builder {
      private final List<StatusHandler.Builder> handlers = new ArrayList<>();

      @SuppressWarnings("unchecked")
      public S handlers(Collection<? extends StatusHandler.Builder> handlers) {
         this.handlers.addAll(handlers);
         return (S) this;
      }

      protected StatusHandler[] buildHandlers() {
         return handlers.stream().map(StatusHandler.Builder::build).toArray(StatusHandler[]::new);
      }
   }
}

