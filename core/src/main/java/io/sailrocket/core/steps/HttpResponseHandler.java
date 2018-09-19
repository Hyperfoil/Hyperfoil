package io.sailrocket.core.steps;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.http.BodyExtractor;
import io.sailrocket.api.http.HeaderExtractor;
import io.sailrocket.api.collection.RequestQueue;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.api.http.StatusExtractor;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.api.http.BodyValidator;
import io.sailrocket.api.http.HeaderValidator;
import io.sailrocket.api.http.StatusValidator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandler implements ResourceUtilizer, Session.ResourceKey<HttpResponseHandler.HandlerInstances>, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandler.class);
   private static final boolean trace = log.isTraceEnabled();

   private final StatusValidator[] statusValidators;
   private final HeaderValidator[] headerValidators;
   private final BodyValidator[] bodyValidators;
   private final StatusExtractor[] statusExtractors;
   private final HeaderExtractor[] headerExtractors;
   private final BodyExtractor[] bodyExtractors;
   private final Consumer<Session>[] completionHandlers;

   private HttpResponseHandler(StatusValidator[] statusValidators,
                              HeaderValidator[] headerValidators,
                              BodyValidator[] bodyValidators,
                              StatusExtractor[] statusExtractors,
                              HeaderExtractor[] headerExtractors,
                              BodyExtractor[] bodyExtractors,
                              Consumer<Session>[] completionHandlers) {
      this.statusValidators = statusValidators;
      this.headerValidators = headerValidators;
      this.bodyValidators = bodyValidators;
      this.statusExtractors = statusExtractors;
      this.headerExtractors = headerExtractors;
      this.bodyExtractors = bodyExtractors;
      this.completionHandlers = completionHandlers;
   }

   private void handleStatus(Session session, int status) {
      if (trace) {
         log.trace("{} Received status {}", this, status);
      }
      RequestQueue.Request request = session.requestQueue().peek();
      session.currentSequence(request.sequence);
      request.sequence.statistics(session).addStatus(status);

      boolean valid = true;
      if (statusValidators != null) {
         for (StatusValidator validator : statusValidators) {
            valid = valid && validator.validate(session, status);
         }
      }
      session.validatorResults().addStatus(valid);
      if (statusExtractors != null) {
         for (StatusExtractor extractor : statusExtractors) {
            extractor.setStatus(status, session);
         }
      }

      // Status is obligatory so we'll init validators/extractors here
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            validator.beforeHeaders(session);
         }
      }
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.beforeHeaders(session);
         }
      }
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            validator.beforeData(session);
         }
      }
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.beforeData(session);
         }
      }
   }

   private void handleHeader(Session session, String header, String value) {
      if (trace) {
         log.trace("{} Received header {}: {}", this, header, value);
      }
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            validator.validateHeader(session, header, value);
         }
      }
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.extractHeader(header, value, session);
         }
      }
   }

   private void handleThrowable(Session session, Throwable throwable) {
      if (trace) {
         log.trace("{} Received exception {}", this, throwable);
      }
   }

   private void handleBodyPart(Session session, ByteBuf buf) {
      if (trace) {
         log.trace("{} Received part:\n{}", this, buf.toString(buf.readerIndex(), buf.readableBytes(), StandardCharsets.UTF_8));
      }

      int dataStartIndex = buf.readerIndex();
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            validator.validateData(session, buf);
            buf.readerIndex(dataStartIndex);
         }
      }
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.extractData(buf, session);
            buf.readerIndex(dataStartIndex);
         }
      }
   }

   private void handleEnd(Session session) {
      long endTime = System.nanoTime();
      RequestQueue.Request request = session.requestQueue().complete();
      Statistics statistics = session.currentSequence().statistics(session);
      statistics.recordValue(endTime - request.startTime);
      statistics.incrementResponses();

      boolean headersValid = true;
      if (headerValidators != null) {
         for (HeaderValidator validator : headerValidators) {
            headersValid = headersValid && validator.validate(session);
         }
      }
      session.validatorResults().addHeader(headersValid);
      if (headerExtractors != null) {
         for (HeaderExtractor extractor : headerExtractors) {
            extractor.afterHeaders(session);
         }
      }
      boolean bodyValid = true;
      if (bodyValidators != null) {
         for (BodyValidator validator : bodyValidators) {
            bodyValid = bodyValid && validator.validate(session);
         }
      }
      session.validatorResults().addBody(bodyValid);
      if (bodyExtractors != null) {
         for (BodyExtractor extractor : bodyExtractors) {
            extractor.afterData(session);
         }
      }
      if (completionHandlers != null) {
         for (Consumer<Session> handler : completionHandlers) {
            handler.accept(session);
         }
      }
      session.currentSequence(null);
      // if anything was blocking due to full request queue we should continue from the right place
      session.proceed(session.executor());
   }


   @Override
   public void reserve(Session session) {
      session.declareResource(this, new HandlerInstances(session));
      reserveAll(session, statusValidators);
      reserveAll(session, headerValidators);
      reserveAll(session, bodyValidators);
      reserveAll(session, statusExtractors);
      reserveAll(session, headerExtractors);
      reserveAll(session, bodyExtractors);
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

   class HandlerInstances implements Session.Resource {
      final IntConsumer handleStatus;
      final BiConsumer<String, String> handleHeader;
      final Consumer<Throwable> handleException;
      final Consumer<ByteBuf> handleBodyPart;
      final Runnable handleEnd;

      private HandlerInstances(Session session) {
         handleStatus = status -> handleStatus(session, status);
         handleHeader = (header, value) -> handleHeader(session, header, value);
         handleException = throwable -> handleThrowable(session, throwable);
         handleBodyPart = body -> handleBodyPart(session, body);
         handleEnd = () -> handleEnd(session);
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
      private List<Consumer<Session>> completionHandlers = new ArrayList<>();

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

      public Builder onCompletion(Consumer<Session> handler) {
         completionHandlers.add(handler);
         return this;
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public HttpResponseHandler build() {
         return new HttpResponseHandler(toArray(statusValidators, new StatusValidator[0]),
               toArray(headerValidators, new HeaderValidator[0]),
               toArray(bodyValidators, new BodyValidator[0]),
               toArray(statusExtractors, new StatusExtractor[0]),
               toArray(headerExtractors, new HeaderExtractor[0]),
               toArray(bodyExtractors, new BodyExtractor[0]),
               toArray(completionHandlers, new Consumer[0]));
      }

      private static <T> T[] toArray(List<T> list, T[] a) {
         return list.isEmpty() ? null : list.toArray(a);
      }

   }
}
