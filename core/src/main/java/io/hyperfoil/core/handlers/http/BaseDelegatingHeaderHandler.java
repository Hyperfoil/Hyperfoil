package io.hyperfoil.core.handlers.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

public abstract class BaseDelegatingHeaderHandler implements HeaderHandler, ResourceUtilizer {
   protected final HeaderHandler[] handlers;

   public BaseDelegatingHeaderHandler(HeaderHandler[] handlers) {
      this.handlers = handlers;
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) handlers);
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

      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> handler() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.Builder.class, this::handler);
      }

      protected HeaderHandler[] buildHandlers() {
         return handlers.stream().map(HeaderHandler.Builder::build).toArray(HeaderHandler[]::new);
      }
   }
}
