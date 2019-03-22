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
import io.hyperfoil.api.http.BodyExtractor;
import io.hyperfoil.api.http.HeaderExtractor;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.http.StatusExtractor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.http.BodyValidator;
import io.hyperfoil.api.http.HeaderValidator;
import io.hyperfoil.api.http.StatusValidator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, ResourceUtilizer, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   final StatusValidator[] statusValidators;
   final HeaderValidator[] headerValidators;
   final BodyValidator[] bodyValidators;
   final StatusExtractor[] statusExtractors;
   final HeaderExtractor[] headerExtractors;
   final BodyExtractor[] bodyExtractors;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusValidator[] statusValidators,
                                    HeaderValidator[] headerValidators,
                                    BodyValidator[] bodyValidators,
                                    StatusExtractor[] statusExtractors,
                                    HeaderExtractor[] headerExtractors,
                                    BodyExtractor[] bodyExtractors,
                                    Action[] completionHandlers,
                                    RawBytesHandler[] rawBytesHandlers) {
      this.statusValidators = statusValidators;
      this.headerValidators = headerValidators;
      this.bodyValidators = bodyValidators;
      this.statusExtractors = statusExtractors;
      this.headerExtractors = headerExtractors;
      this.bodyExtractors = bodyExtractors;
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

      boolean valid = true;
      if (statusValidators != null) {
         for (StatusValidator validator : statusValidators) {
            valid = valid && validator.validate(request, status);
         }
      }
      session.validatorResults().addStatus(valid);
      if (statusExtractors != null) {
         for (StatusExtractor extractor : statusExtractors) {
            extractor.setStatus(request, status);
         }
      }

      // Status is obligatory so we'll init validators/extractors here
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            validator.beforeHeaders(request);
         }
      }
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.beforeHeaders(request);
         }
      }
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            validator.beforeData(request);
         }
      }
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.beforeData(request);
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
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            validator.validateHeader(request, header, value);
         }
      }
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.extractHeader(request, header, value);
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
      request.setCompleted();
      request.statistics().incrementResets();
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
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            validator.validateData(request, buf);
            buf.readerIndex(dataStartIndex);
         }
      }
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.extractData(request, buf);
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

      boolean headersValid = true;
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            headersValid = headersValid && validator.validate(request);
         }
      }
      session.validatorResults().addHeader(headersValid);
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.afterHeaders(request);
         }
      }
      boolean bodyValid = true;
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            bodyValid = bodyValid && validator.validate(request);
         }
      }
      session.validatorResults().addBody(bodyValid);
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.afterData(request);
         }
      }
      if (completionHandlers != null) {
         for (Action handler : completionHandlers) {
            handler.run(session);
         }
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
      reserveAll(session, statusValidators);
      reserveAll(session, headerValidators);
      reserveAll(session, bodyValidators);
      reserveAll(session, statusExtractors);
      reserveAll(session, headerExtractors);
      reserveAll(session, bodyExtractors);
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
      private List<StatusValidator.Builder> statusValidators = new ArrayList<>();
      private List<HeaderValidator.Builder> headerValidators = new ArrayList<>();
      private List<BodyValidator.Builder> bodyValidators = new ArrayList<>();
      private List<StatusExtractor.Builder> statusExtractors = new ArrayList<>();
      private List<HeaderExtractor.Builder> headerExtractors = new ArrayList<>();
      private List<BodyExtractor.Builder> bodyExtractors = new ArrayList<>();
      private List<Action.Builder> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler> rawBytesHandlers = new ArrayList<>();

      public static Builder forTesting() {
         return new Builder(null);
      }

      Builder(HttpRequestStep.Builder parent) {
         this.parent = parent;
      }

      public Builder statusValidator(StatusValidator validator) {
         statusValidators.add(() -> validator);
         return this;
      }

      public ServiceLoadedBuilderProvider<StatusValidator.Builder> statusValidator() {
         return new ServiceLoadedBuilderProvider<>(StatusValidator.BuilderFactory.class, parent, statusValidators::add);
      }

      public Builder headerValidator(HeaderValidator validator) {
         headerValidators.add(() -> validator);
         return this;
      }

      public ServiceLoadedBuilderProvider<HeaderValidator.Builder> headerValidator() {
         return new ServiceLoadedBuilderProvider<>(HeaderValidator.BuilderFactory.class, parent, headerValidators::add);
      }

      public Builder bodyValidator(BodyValidator validator) {
         bodyValidators.add(()-> validator);
         return this;
      }

      public ServiceLoadedBuilderProvider<BodyValidator.Builder> bodyValidator() {
         return new ServiceLoadedBuilderProvider<>(BodyValidator.BuilderFactory.class, parent, bodyValidators::add);
      }

      public Builder statusExtractor(StatusExtractor extractor) {
         statusExtractors.add(() -> extractor);
         return this;
      }

      public ServiceLoadedBuilderProvider<StatusExtractor.Builder> statusExtractor() {
         return new ServiceLoadedBuilderProvider<>(StatusExtractor.BuilderFactory.class, parent, statusExtractors::add);
      }

      public Builder headerExtractor(HeaderExtractor extractor) {
         headerExtractors.add(() -> extractor);
         return this;
      }

      public ServiceLoadedBuilderProvider<HeaderExtractor.Builder> headerExtractor() {
         return new ServiceLoadedBuilderProvider<>(HeaderExtractor.BuilderFactory.class, parent, headerExtractors::add);
      }

      public Builder bodyExtractor(BodyExtractor extractor) {
         bodyExtractors.add(() -> extractor);
         return this;
      }

      public ServiceLoadedBuilderProvider<BodyExtractor.Builder> bodyExtractor() {
         return new ServiceLoadedBuilderProvider<>(BodyExtractor.BuilderFactory.class, parent, bodyExtractors::add);
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
            headerExtractor(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusValidators.forEach(StatusValidator.Builder::prepareBuild);
         headerValidators.forEach(HeaderValidator.Builder::prepareBuild);
         bodyValidators.forEach(BodyValidator.Builder::prepareBuild);
         statusExtractors.forEach(StatusExtractor.Builder::prepareBuild);
         headerExtractors.forEach(HeaderExtractor.Builder::prepareBuild);
         bodyExtractors.forEach(BodyExtractor.Builder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusValidators, StatusValidator.Builder::build, StatusValidator[]::new),
               toArray(headerValidators, HeaderValidator.Builder::build, HeaderValidator[]::new),
               toArray(bodyValidators, BodyValidator.Builder::build, BodyValidator[]::new),
               toArray(statusExtractors, StatusExtractor.Builder::build, StatusExtractor[]::new),
               toArray(headerExtractors, HeaderExtractor.Builder::build, HeaderExtractor[]::new),
               toArray(bodyExtractors, BodyExtractor.Builder::build, BodyExtractor[]::new),
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
