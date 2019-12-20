package io.hyperfoil.core.steps;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.http.CookieRecorder;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, ResourceUtilizer, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   final StatusHandler[] statusHandlers;
   final HeaderHandler[] headerHandlers;
   final Processor<HttpRequest>[] bodyHandlers;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusHandler[] statusHandlers,
                                    HeaderHandler[] headerHandlers,
                                    Processor<HttpRequest>[] bodyHandlers,
                                    Action[] completionHandlers,
                                    RawBytesHandler[] rawBytesHandlers) {
      this.statusHandlers = statusHandlers;
      this.headerHandlers = headerHandlers;
      this.bodyHandlers = bodyHandlers;
      this.completionHandlers = completionHandlers;
      this.rawBytesHandlers = rawBytesHandlers;
   }

   @Override
   public void handleStatus(HttpRequest request, int status, String reason) {
      Session session = request.session;
      session.currentSequence(request.sequence());
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring status {} as the request has been marked completed (failed).", session.uniqueId(), status);
         }
         return;
      }

      if (trace) {
         log.trace("#{} Received status {}: {}", session.uniqueId(), status, reason);
      }

      switch (request.method) {
         case GET:
         case HEAD:
            // TODO: should we store 203, 300, 301 and 410?
            // TODO: partial response 206
            if (status != 200) {
               request.cacheControl.noStore = true;
            }
            break;
         case POST:
         case PUT:
         case DELETE:
         case PATCH:
            if (status >= 200 && status <= 399) {
               request.session.httpCache().invalidate(request.authority, request.path);
               request.cacheControl.invalidate = true;
            }
            request.cacheControl.noStore = true;
            break;
      }

      request.statistics().addStatus(request.startTimestampMillis(), status);
      if (statusHandlers != null) {
         for (StatusHandler handler : statusHandlers) {
            handler.handleStatus(request, status);
         }
      }

      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.beforeHeaders(request);
         }
      }
      if (bodyHandlers != null) {
         for (Processor<HttpRequest> handler : bodyHandlers) {
            handler.before(request);
         }
      }
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring header on a failed request: {}: {}", session.uniqueId(), header, value);
         }
         return;
      }
      if (trace) {
         log.trace("#{} Received header {}: {}", session.uniqueId(), header, value);
      }
      if (request.cacheControl.invalidate) {
         if (AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.LOCATION)
               || AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.CONTENT_LOCATION)) {
            session.httpCache().invalidate(request.authority, value);
         }
      }
      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.handleHeader(request, header, value);
         }
      }
      request.session.httpCache().responseHeader(request, header, value);
   }

   @Override
   public void handleThrowable(HttpRequest request, Throwable throwable) {
      Session session = request.session;
      if (trace) {
         log.trace("#{} Received exception {}", session.uniqueId(), throwable);
      }
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Request has been already completed", session.uniqueId());
         }
         return;
      }

      if (completionHandlers != null) {
         for (Action handler : completionHandlers) {
            handler.run(session);
         }
      }
      request.statistics().incrementResets(request.startTimestampMillis());
      request.setCompleted();
      session.httpRequestPool().release(request);
      session.currentSequence(null);
      session.proceed();
   }

   @Override
   public void handleBodyPart(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring body part ({} bytes) on a failed request.", session.uniqueId(), data.readableBytes());
         }
         return;
      }

      if (trace) {
         log.trace("#{} Received part ({} bytes):\n{}", session.uniqueId(), data.readableBytes(),
               data.toString(data.readerIndex(), data.readableBytes(), StandardCharsets.UTF_8));
      }

      int dataStartIndex = data.readerIndex();
      if (bodyHandlers != null) {
         for (Processor<HttpRequest> handler : bodyHandlers) {
            handler.process(request, data, offset, length, isLastPart);
            data.readerIndex(dataStartIndex);
         }
      }
   }

   @Override
   public boolean hasRawBytesHandler() {
      return rawBytesHandlers != null && rawBytesHandlers.length > 0;
   }

   @Override
   public void handleEnd(HttpRequest request, boolean executed) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Request has been already completed.", session.uniqueId());
         }
         return;
      }
      if (trace) {
         log.trace("#{} Completed request on {}", session.uniqueId(), request.connection());
      }

      if (executed) {
         long endTime = System.nanoTime();
         request.statistics().recordResponse(request.startTimestampMillis(), request.sendTimestampNanos() - request.startTimestampNanos(), endTime - request.startTimestampNanos());

         if (headerHandlers != null) {
            for (HeaderHandler handler : headerHandlers) {
               handler.afterHeaders(request);
            }
         }
         if (bodyHandlers != null) {
            for (Processor<HttpRequest> handler : bodyHandlers) {
               handler.after(request);
            }
         }
         request.session.httpCache().tryStore(request);
      }

      if (completionHandlers != null) {
         for (Action handler : completionHandlers) {
            handler.run(session);
         }
      }

      if (executed && !request.isValid()) {
         request.statistics().addInvalid(request.startTimestampMillis());
      }
      request.setCompleted();
      session.httpRequestPool().release(request);
      session.currentSequence(null);
      // if anything was blocking due to full request queue we should continue from the right place
      session.proceed();
   }

   @Override
   public void handleRawBytes(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.accept(request, data, offset, length, isLastPart);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) statusHandlers);
      ResourceUtilizer.reserve(session, (Object[]) headerHandlers);
      ResourceUtilizer.reserve(session, (Object[]) bodyHandlers);
      ResourceUtilizer.reserve(session, (Object[]) completionHandlers);
      ResourceUtilizer.reserve(session, (Object[]) rawBytesHandlers);
   }

   /**
    * Manages processing of HTTP responses.
    */
   public static class Builder {
      private final HttpRequestStep.Builder parent;
      private Locator locator;
      private List<StatusHandler.Builder> statusHandlers = new ArrayList<>();
      private List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
      private List<HttpRequestProcessorBuilder> bodyHandlers = new ArrayList<>();
      private List<Action.Builder> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler.Builder> rawBytesHandlers = new ArrayList<>();

      public static Builder forTesting() {
         return new Builder(null);
      }

      Builder(HttpRequestStep.Builder parent) {
         this.parent = parent;
      }

      public Builder status(StatusHandler handler) {
         statusHandlers.add(() -> handler);
         return this;
      }

      /**
       * Handle HTTP response status.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<StatusHandler.Builder> status() {
         return new ServiceLoadedBuilderProvider<>(StatusHandler.Builder.class, locator, statusHandlers::add);
      }

      public Builder header(HeaderHandler handler) {
         return header(() -> handler);
      }

      public Builder header(HeaderHandler.Builder builder) {
         headerHandlers.add(builder.setLocator(locator));
         return this;
      }

      /**
       * Handle HTTP response headers.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> header() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.Builder.class, locator, headerHandlers::add);
      }

      public Builder body(Processor<HttpRequest> handler) {
         bodyHandlers.add(() -> handler);
         return this;
      }

      /**
       * Handle HTTP response body.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HttpRequestProcessorBuilder> body() {
         return new ServiceLoadedBuilderProvider<>(HttpRequestProcessorBuilder.class, locator, bodyHandlers::add);
      }

      public Builder onCompletion(Action handler) {
         return onCompletion(() -> handler);
      }

      public Builder onCompletion(Action.Builder builder) {
         completionHandlers.add(builder.setLocator(locator));
         return this;
      }

      /**
       * Action executed when the HTTP response is fully received.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, locator, completionHandlers::add);
      }

      public Builder rawBytes(RawBytesHandler handler) {
         rawBytesHandlers.add(() -> handler);
         return this;
      }

      public Builder rawBytes(RawBytesHandler.Builder builder) {
         rawBytesHandlers.add(builder);
         return this;
      }

      /**
       * Handler processing HTTP response before parsing.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<RawBytesHandler.Builder> rawBytes() {
         return new ServiceLoadedBuilderProvider<>(RawBytesHandler.Builder.class, locator, this::rawBytes);
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public void prepareBuild() {
         if (locator.scenario().endScenario().endPhase().ergonomics().repeatCookies()) {
            header(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusHandlers.forEach(StatusHandler.Builder::prepareBuild);
         headerHandlers.forEach(HeaderHandler.Builder::prepareBuild);
         bodyHandlers.forEach(HttpRequestProcessorBuilder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
         rawBytesHandlers.forEach(RawBytesHandler.Builder::prepareBuild);
      }

      @SuppressWarnings("unchecked")
      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusHandlers, StatusHandler.Builder::build, StatusHandler[]::new),
               toArray(headerHandlers, HeaderHandler.Builder::build, HeaderHandler[]::new),
               toArray(bodyHandlers, Processor.Builder::build, Processor[]::new),
               toArray(completionHandlers, Action.Builder::build, Action[]::new),
               toArray(rawBytesHandlers, RawBytesHandler.Builder::build, RawBytesHandler[]::new));
      }

      private static <B, T> T[] toArray(List<B> list, Function<B, T> build, IntFunction<T[]> generator) {
         if (list.isEmpty()) {
            return null;
         } else {
            return list.stream().map(build).toArray(generator);
         }
      }

      public HttpResponseHandlersImpl.Builder copy(HttpRequestStep.Builder parent, Locator locator) {
         Builder copy = new Builder(parent).setLocator(locator);
         copy.statusHandlers.addAll(BuilderBase.copy(locator, this.statusHandlers));
         copy.headerHandlers.addAll(BuilderBase.copy(locator, this.headerHandlers));
         copy.bodyHandlers.addAll(BuilderBase.copy(locator, this.bodyHandlers));
         copy.completionHandlers.addAll(BuilderBase.copy(locator, this.completionHandlers));
         copy.rawBytesHandlers.addAll(BuilderBase.copy(locator, this.rawBytesHandlers));
         return copy;
      }

      public Builder setLocator(Locator locator) {
         this.locator = locator;
         statusHandlers.forEach(builder -> builder.setLocator(locator));
         headerHandlers.forEach(builder -> builder.setLocator(locator));
         bodyHandlers.forEach(builder -> builder.setLocator(locator));
         completionHandlers.forEach(builder -> builder.setLocator(locator));
         rawBytesHandlers.forEach(builder -> builder.setLocator(locator));
         return this;
      }
   }
}
