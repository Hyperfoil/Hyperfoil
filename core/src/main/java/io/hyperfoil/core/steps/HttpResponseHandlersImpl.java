package io.hyperfoil.core.steps;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.http.CookieRecorder;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.BodyHandler;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, ResourceUtilizer, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   final StatusHandler[] statusHandlers;
   final HeaderHandler[] headerHandlers;
   final BodyHandler[] bodyHandlers;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusHandler[] statusHandlers,
                                    HeaderHandler[] headerHandlers,
                                    BodyHandler[] bodyHandlers,
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

      request.statistics().addStatus(status);
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
         for (BodyHandler handler : bodyHandlers) {
            handler.beforeData(request);
         }
      }
   }

   @Override
   public void handleHeader(HttpRequest request, String header, String value) {
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
      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.handleHeader(request, header, value);
         }
      }
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
      request.statistics().incrementResets();
      request.setCompleted();
      session.httpRequestPool().release(request);
      session.currentSequence(null);
      session.proceed();
   }

   @Override
   public void handleBodyPart(HttpRequest request, ByteBuf buf) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring body part ({} bytes) on a failed request.", session.uniqueId(), buf.readableBytes());
         }
         return;
      }

      if (trace) {
         log.trace("#{} Received part ({} bytes):\n{}", session.uniqueId(), buf.readableBytes(),
               buf.toString(buf.readerIndex(), buf.readableBytes(), StandardCharsets.UTF_8));
      }

      int dataStartIndex = buf.readerIndex();
      if (bodyHandlers != null) {
         for (BodyHandler handler : bodyHandlers) {
            handler.handleData(request, buf);
            buf.readerIndex(dataStartIndex);
         }
      }
   }

   @Override
   public boolean hasRawBytesHandler() {
      return rawBytesHandlers != null && rawBytesHandlers.length > 0;
   }

   @Override
   public void handleEnd(HttpRequest request) {
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

      long endTime = System.nanoTime();
      request.statistics().recordResponse(request.sendTime() - request.startTime(), endTime - request.startTime());

      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.afterHeaders(request);
         }
      }
      if (bodyHandlers != null) {
         for (BodyHandler handler : bodyHandlers) {
            handler.afterData(request);
         }
      }
      if (completionHandlers != null) {
         for (Action handler : completionHandlers) {
            handler.run(session);
         }
      }

      if (!request.isValid()) {
         request.statistics().addInvalid();
      }
      request.setCompleted();
      session.httpRequestPool().release(request);
      session.currentSequence(null);
      // if anything was blocking due to full request queue we should continue from the right place
      session.proceed();
   }

   @Override
   public void handleRawBytes(HttpRequest request, ByteBuf buf) {
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.accept(request, buf);
      }
   }

   @Override
   public void reserve(Session session) {
      reserveAll(session, statusHandlers);
      reserveAll(session, headerHandlers);
      reserveAll(session, bodyHandlers);
      reserveAll(session, rawBytesHandlers);
   }

   private <T> void reserveAll(Session session, T[] items) {
      if (items != null) {
         for (T item : items) {
            if (item instanceof ResourceUtilizer) {
               ((ResourceUtilizer) item).reserve(session);
            }
         }
      }
   }

   public static class Builder {
      private final HttpRequestStep.Builder parent;
      private List<StatusHandler.Builder> statusHandlers = new ArrayList<>();
      private List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
      private List<BodyHandler.Builder> bodyHandlers = new ArrayList<>();
      private List<Action.Builder> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler> rawBytesHandlers = new ArrayList<>();

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

      public ServiceLoadedBuilderProvider<StatusHandler.Builder> status() {
         return new ServiceLoadedBuilderProvider<>(StatusHandler.BuilderFactory.class, parent, statusHandlers::add);
      }

      public Builder header(HeaderHandler handler) {
         headerHandlers.add(() -> handler);
         return this;
      }

      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> header() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.BuilderFactory.class, parent, headerHandlers::add);
      }

      public Builder body(BodyHandler handler) {
         bodyHandlers.add(() -> handler);
         return this;
      }

      public ServiceLoadedBuilderProvider<BodyHandler.Builder> body() {
         return new ServiceLoadedBuilderProvider<>(BodyHandler.BuilderFactory.class, parent, bodyHandlers::add);
      }

      public Builder onCompletion(Action handler) {
         completionHandlers.add(() -> handler);
         return this;
      }

      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.BuilderFactory.class, parent, completionHandlers::add);
      }

      public Builder rawBytesHandler(RawBytesHandler handler) {
         rawBytesHandlers.add(handler);
         return this;
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public void prepareBuild() {
         if (parent.endStep().endSequence().endScenario().endPhase().ergonomics().repeatCookies()) {
            header(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusHandlers.forEach(StatusHandler.Builder::prepareBuild);
         headerHandlers.forEach(HeaderHandler.Builder::prepareBuild);
         bodyHandlers.forEach(BodyHandler.Builder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusHandlers, StatusHandler.Builder::build, StatusHandler[]::new),
               toArray(headerHandlers, HeaderHandler.Builder::build, HeaderHandler[]::new),
               toArray(bodyHandlers, BodyHandler.Builder::build, BodyHandler[]::new),
               toArray(completionHandlers, Action.Builder::build, Action[]::new),
               toArray(rawBytesHandlers, Function.identity(), RawBytesHandler[]::new));
      }

      private static <B, T> T[] toArray(List<B> list, Function<B, T> build, IntFunction<T[]> generator) {
         if (list.isEmpty()) {
            return null;
         } else {
            return list.stream().map(build).toArray(generator);
         }
      }
   }
}
