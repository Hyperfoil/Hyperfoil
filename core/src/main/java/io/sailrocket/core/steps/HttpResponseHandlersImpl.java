package io.sailrocket.core.steps;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.http.BodyExtractor;
import io.sailrocket.api.http.HeaderExtractor;
import io.sailrocket.api.http.HttpResponseHandlers;
import io.sailrocket.api.http.RawBytesHandler;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.api.http.StatusExtractor;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.api.http.BodyValidator;
import io.sailrocket.api.http.HeaderValidator;
import io.sailrocket.api.http.StatusValidator;
import io.sailrocket.function.SerializableConsumer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, ResourceUtilizer, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   private final StatusValidator[] statusValidators;
   private final HeaderValidator[] headerValidators;
   private final BodyValidator[] bodyValidators;
   private final StatusExtractor[] statusExtractors;
   private final HeaderExtractor[] headerExtractors;
   private final BodyExtractor[] bodyExtractors;
   private final SerializableConsumer<Session>[] completionHandlers;
   private final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusValidator[] statusValidators,
                                    HeaderValidator[] headerValidators,
                                    BodyValidator[] bodyValidators,
                                    StatusExtractor[] statusExtractors,
                                    HeaderExtractor[] headerExtractors,
                                    BodyExtractor[] bodyExtractors,
                                    SerializableConsumer<Session>[] completionHandlers,
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
   public void handleStatus(Request request, int status) {
      Session session = request.session;
      session.currentSequence(request.sequence());
      if (request.isCompleted()) {
         log.trace("#{} Ignoring status {} as the request has been marked completed (failed).", session.uniqueId(), status);
         return;
      }

      if (trace) {
         log.trace("#{} Received status {}", session.uniqueId(), status);
      }
      request.sequence().statistics(session).addStatus(status);

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
   public void handleHeader(Request request, String header, String value) {
      Session session = request.session;
      if (request.isCompleted()) {
         log.trace("#{} Ignoring header on a failed request: {}: {}", session.uniqueId(), header, value);
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
   public void handleThrowable(Request request, Throwable throwable) {
      Session session = request.session;
      if (trace) {
         log.trace("#{} Received exception {}", session.uniqueId(), throwable);
      }

      if (completionHandlers != null) {
         for (Consumer<Session> handler : completionHandlers) {
            handler.accept(session);
         }
      }
      request.setCompleted();
      request.sequence().statistics(session).incrementResets();
      session.requestPool().release(request);
      session.currentSequence(null);
      session.proceed();
   }

   @Override
   public void handleBodyPart(Request request, ByteBuf buf) {
      Session session = request.session;
      if (request.isCompleted()) {
         log.trace("#{} Ignoring body part ({} bytes) on a failed request.", session.uniqueId(), buf.readableBytes());
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
   public void handleEnd(Request request) {
      if (request.isCompleted()) {
         return;
      }
      Session session = request.session;
      long endTime = System.nanoTime();
      Statistics statistics = session.currentSequence().statistics(session);
      statistics.recordValue(endTime - request.startTime());
      statistics.incrementResponses();

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
         for (Consumer<Session> handler : completionHandlers) {
            handler.accept(session);
         }
      }

      request.setCompleted();
      session.requestPool().release(request);
      session.currentSequence(null);
      // if anything was blocking due to full request queue we should continue from the right place
      session.proceed();
   }

   @Override
   public void handleRawBytes(Request request, ByteBuf buf) {
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
      private List<StatusValidator> statusValidators = new ArrayList<>();
      private List<HeaderValidator> headerValidators = new ArrayList<>();
      private List<BodyValidator> bodyValidators = new ArrayList<>();
      private List<StatusExtractor> statusExtractors = new ArrayList<>();
      private List<HeaderExtractor> headerExtractors = new ArrayList<>();
      private List<BodyExtractor> bodyExtractors = new ArrayList<>();
      private List<SerializableConsumer<Session>> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler> rawBytesHandlers = new ArrayList<>();

      public static Builder forTesting() {
         return new Builder(null);
      }

      Builder(HttpRequestStep.Builder parent) {
         this.parent = parent;
      }

      public Builder statusValidator(StatusValidator validator) {
         statusValidators.add(validator);
         return this;
      }

      public Builder headerValidator(HeaderValidator validator) {
         headerValidators.add(validator);
         return this;
      }

      public Builder bodyValidator(BodyValidator validator) {
         bodyValidators.add(validator);
         return this;
      }

      public Builder statusExtractor(StatusExtractor extractor) {
         statusExtractors.add(extractor);
         return this;
      }

      public Builder headerExtractor(HeaderExtractor extractor) {
         headerExtractors.add(extractor);
         return this;
      }

      public Builder bodyExtractor(BodyExtractor extractor) {
         bodyExtractors.add(extractor);
         return this;
      }

      public Builder onCompletion(SerializableConsumer<Session> handler) {
         completionHandlers.add(handler);
         return this;
      }

      public Builder rawBytesHandler(RawBytesHandler handler) {
         rawBytesHandlers.add(handler);
         return this;
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(toArray(statusValidators, StatusValidator.class),
               toArray(headerValidators, HeaderValidator.class),
               toArray(bodyValidators, BodyValidator.class),
               toArray(statusExtractors, StatusExtractor.class),
               toArray(headerExtractors, HeaderExtractor.class),
               toArray(bodyExtractors, BodyExtractor.class),
               toArray(completionHandlers, SerializableConsumer.class),
               toArray(rawBytesHandlers, RawBytesHandler.class));
      }

      private static <T> T[] toArray(List<T> list, Class<?> component) {
         if (list.isEmpty()) {
            return null;
         } else {
            @SuppressWarnings("unchecked")
            T[] empty = (T[]) Array.newInstance(component, list.size());
            return list.toArray(empty);
         }
      }
   }
}
