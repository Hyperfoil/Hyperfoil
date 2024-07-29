package io.hyperfoil.http.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.http.api.HttpRequest;

public abstract class BaseDelegatingHeaderHandler implements HeaderHandler {
   protected final HeaderHandler[] handlers;

   public BaseDelegatingHeaderHandler(HeaderHandler[] handlers) {
      this.handlers = handlers;
   }

   @Override
   public void beforeHeaders(HttpRequest request) {
      for (HeaderHandler h : handlers) {
         h.beforeHeaders(request);
      }
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      for (HeaderHandler h : handlers) {
         h.handleHeader(request, header, value);
      }
   }

   @Override
   public void afterHeaders(HttpRequest request) {
      for (HeaderHandler h : handlers) {
         h.afterHeaders(request);
      }
   }

   public abstract static class Builder<S extends Builder<S>> implements HeaderHandler.Builder {
      protected final List<HeaderHandler.Builder> handlers = new ArrayList<>();

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      public S handler(HeaderHandler.Builder handler) {
         this.handlers.add(handler);
         return self();
      }

      public S handlers(Collection<? extends HeaderHandler.Builder> handlers) {
         this.handlers.addAll(handlers);
         return self();
      }

      /**
       * One or more header handlers that should be invoked.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> handler() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.Builder.class, this::handler);
      }

      protected HeaderHandler[] buildHandlers() {
         return handlers.stream().map(HeaderHandler.Builder::build).toArray(HeaderHandler[]::new);
      }
   }
}
