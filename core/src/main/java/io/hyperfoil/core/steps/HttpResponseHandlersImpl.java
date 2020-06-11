package io.hyperfoil.core.steps;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Rewritable;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.handlers.RangeStatusValidator;
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
   final Processor[] bodyHandlers;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusHandler[] statusHandlers,
                                    HeaderHandler[] headerHandlers,
                                    Processor[] bodyHandlers,
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
         for (Processor handler : bodyHandlers) {
            handler.before(request.session);
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
         log.trace("#{} {} Received exception", throwable, session.uniqueId(), request);
      }
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Request has been already completed", session.uniqueId());
         }
         return;
      }
      if (request.isValid()) {
         // Do not mark as invalid when timed out
         request.markInvalid();
      }
      session.currentRequest(request);

      try {
         if (request.isRunning()) {
            request.setCompleting();
            if (completionHandlers != null) {
               for (Action handler : completionHandlers) {
                  handler.run(session);
               }
            }
         }
      } finally {
         request.statistics().incrementResets(request.startTimestampMillis());
         request.setCompleted();
      }
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
      session.currentRequest(request);

      if (trace) {
         log.trace("#{} Received part ({} bytes):\n{}", session.uniqueId(), data.readableBytes(),
               data.toString(data.readerIndex(), data.readableBytes(), StandardCharsets.UTF_8));
      }

      int dataStartIndex = data.readerIndex();
      if (bodyHandlers != null) {
         for (Processor handler : bodyHandlers) {
            handler.process(request.session, data, offset, length, isLastPart);
            data.readerIndex(dataStartIndex);
         }
      }
      session.currentRequest(null);
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
      session.currentRequest(request);
      if (trace) {
         log.trace("#{} Completed request on {}", session.uniqueId(), request.connection());
      }

      try {
         if (executed) {
            long endTime = System.nanoTime();
            request.statistics().recordResponse(request.startTimestampMillis(), request.sendTimestampNanos() - request.startTimestampNanos(), endTime - request.startTimestampNanos());

            if (headerHandlers != null) {
               for (HeaderHandler handler : headerHandlers) {
                  handler.afterHeaders(request);
               }
            }
            if (bodyHandlers != null) {
               for (Processor handler : bodyHandlers) {
                  handler.after(request.session);
               }
            }
            request.session.httpCache().tryStore(request);
         }

         if (request.isRunning()) {
            request.setCompleting();
            if (completionHandlers != null) {
               for (Action handler : completionHandlers) {
                  handler.run(session);
               }
            }
         }
      } finally {
         if (executed && !request.isValid()) {
            request.statistics().addInvalid(request.startTimestampMillis());
         }
         request.setCompleted();
      }
   }

   @Override
   public void handleRawRequest(HttpRequest request, ByteBuf data, int offset, int length) {
      if (rawBytesHandlers == null) {
         return;
      }
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.onRequest(request, data, offset, length);
      }
   }

   @Override
   public void handleRawResponse(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (rawBytesHandlers == null) {
         return;
      }
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.onResponse(request, data, offset, length, isLastPart);
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
   public static class Builder implements Rewritable<Builder> {
      // prevents some weird serialization incompatibility
      private static final Action STOP_ON_INVALID_RESPONSE = session -> {
         if (!session.currentRequest().isValid()) {
            session.stop();
         }
      };
      private final HttpRequestStep.Builder parent;
      private Boolean autoRangeCheck;
      private Boolean stopOnInvalid;
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

      public Builder status(StatusHandler.Builder builder) {
         statusHandlers.add(builder);
         return this;
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
         return new ServiceLoadedBuilderProvider<>(StatusHandler.Builder.class, statusHandlers::add);
      }

      public Builder header(HeaderHandler handler) {
         return header(() -> handler);
      }

      public Builder header(HeaderHandler.Builder builder) {
         headerHandlers.add(builder);
         return this;
      }

      /**
       * Handle HTTP response headers.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> header() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.Builder.class, headerHandlers::add);
      }

      public Builder body(HttpRequestProcessorBuilder builder) {
         bodyHandlers.add(builder);
         return this;
      }

      /**
       * Handle HTTP response body.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HttpRequestProcessorBuilder> body() {
         return new ServiceLoadedBuilderProvider<>(HttpRequestProcessorBuilder.class, bodyHandlers::add);
      }

      public Builder onCompletion(Action handler) {
         return onCompletion(() -> handler);
      }

      public Builder onCompletion(Action.Builder builder) {
         completionHandlers.add(builder);
         return this;
      }

      /**
       * Action executed when the HTTP response is fully received.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, completionHandlers::add);
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
         return new ServiceLoadedBuilderProvider<>(RawBytesHandler.Builder.class, this::rawBytes);
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public void prepareBuild() {
         ErgonomicsBuilder ergonomics = Locator.current().benchmark().ergonomics();
         if (ergonomics.repeatCookies()) {
            header(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusHandlers.forEach(StatusHandler.Builder::prepareBuild);
         headerHandlers.forEach(HeaderHandler.Builder::prepareBuild);
         bodyHandlers.forEach(HttpRequestProcessorBuilder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
         rawBytesHandlers.forEach(RawBytesHandler.Builder::prepareBuild);
         if (autoRangeCheck == null && ergonomics.autoRangeCheck() || autoRangeCheck != null && autoRangeCheck) {
            statusHandlers.add(new RangeStatusValidator.Builder().min(200).max(399));
         }
         // We must add this as the very last action since after calling session.stop() there other handlers won't be called
         if (stopOnInvalid == null && ergonomics.stopOnInvalid() || stopOnInvalid != null && stopOnInvalid) {
            completionHandlers.add(() -> STOP_ON_INVALID_RESPONSE);
         }
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusHandlers, StatusHandler.Builder::build, StatusHandler[]::new),
               toArray(headerHandlers, HeaderHandler.Builder::build, HeaderHandler[]::new),
               toArray(bodyHandlers, b -> b.build(true), Processor[]::new),
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

      @Override
      public void readFrom(Builder other) {
         statusHandlers.addAll(BuilderBase.copy(other.statusHandlers));
         headerHandlers.addAll(BuilderBase.copy(other.headerHandlers));
         bodyHandlers.addAll(BuilderBase.copy(other.bodyHandlers));
         completionHandlers.addAll(BuilderBase.copy(other.completionHandlers));
         rawBytesHandlers.addAll(BuilderBase.copy(other.rawBytesHandlers));
      }
   }
}
