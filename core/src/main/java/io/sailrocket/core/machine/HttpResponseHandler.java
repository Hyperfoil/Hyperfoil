package io.sailrocket.core.machine;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.BodyExtractor;
import io.sailrocket.api.HeaderExtractor;
import io.sailrocket.api.StatusExtractor;
import io.sailrocket.spi.BodyValidator;
import io.sailrocket.spi.HeaderValidator;
import io.sailrocket.spi.StatusValidator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandler implements ResourceUtilizer, io.sailrocket.api.Session.ResourceKey<HttpResponseHandler.HandlerInstances> {
   private static final Logger log = LoggerFactory.getLogger(State.class);
   private static final boolean trace = log.isTraceEnabled();

   private StatusValidator[] statusValidators;
   private HeaderValidator[] headerValidators;
   private BodyValidator[] bodyValidators;
   private StatusExtractor[] statusExtractors;
   private HeaderExtractor[] headerExtractors;
   private BodyExtractor[] bodyExtractors;

   private void handleStatus(Session session, int status) {
      if (trace) {
         log.trace("{} Received status {}", this, status);
      }
      session.statistics().addStatus(status);

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
      session.statistics().histogram.recordValue(endTime - request.startTime);
      session.statistics().responseCount++;

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
      // if anything was blocking due to full request queue we should continue from the right place
      session.run();
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

   public HttpResponseHandler addStatusValidator(StatusValidator validator) {
      statusValidators = append(statusValidators, validator, StatusValidator.class);
      return this;
   }

   public HttpResponseHandler addHeaderValidator(HeaderValidator validator) {
      headerValidators = append(headerValidators, validator, HeaderValidator.class);
      return this;
   }

   public HttpResponseHandler addBodyValidator(BodyValidator validator) {
      bodyValidators = append(bodyValidators, validator, BodyValidator.class);
      return this;
   }

   public HttpResponseHandler addStatusExtractor(StatusExtractor extractor) {
      statusExtractors = append(statusExtractors, extractor, StatusExtractor.class);
      return this;
   }

   public HttpResponseHandler addHeaderExtractor(HeaderExtractor extractor) {
      headerExtractors = append(headerExtractors, extractor, HeaderExtractor.class);
      return this;
   }

   public HttpResponseHandler addBodyExtractor(BodyExtractor extractor) {
      bodyExtractors = append(bodyExtractors, extractor, BodyExtractor.class);
      return this;
   }

   private static <T> T[] append(T[] array, T item, Class<T> type) {
      if (array == null) {
         array = (T[]) Array.newInstance(type, 1);
      } else {
         T[] newArray = (T[]) Array.newInstance(type, array.length + 1);
         System.arraycopy(array, 0, newArray, 0, array.length);
         array = newArray;
      }
      array[array.length - 1] = item;
      return array;
   }

   class HandlerInstances implements io.sailrocket.api.Session.Resource {
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
}
