package io.sailrocket.core.machine;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.BodyExtractor;
import io.sailrocket.api.HeaderExtractor;
import io.sailrocket.api.StatusExtractor;
import io.sailrocket.spi.BodyValidator;
import io.sailrocket.spi.HeaderValidator;
import io.sailrocket.spi.StatusValidator;

public class HttpResponseState extends State {
   static final String HANDLE_STATUS = "status";
   static final String HANDLE_HEADER = "header";
   static final String HANDLE_EXCEPTION = "exception";
   static final String HANDLE_BODY_PART = "body_part";
   static final String HANDLE_END = "end";

   private StatusValidator[] statusValidators;
   private HeaderValidator[] headerValidators;
   private BodyValidator[] bodyValidators;
   private StatusExtractor[] statusExtractors;
   private HeaderExtractor[] headerExtractors;
   private BodyExtractor[] bodyExtractors;

   public HttpResponseState(String name) {
      super(name);
   }

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
   public void register(Session session) {
      super.register(session);
      session.registerIntHandler(this, HANDLE_STATUS, status -> handleStatus(session, status));
      session.registerBiHandler(this, HANDLE_EXCEPTION, (header, value) -> handleHeader(session, (String) header, (String) value));
      session.registerExceptionHandler(this, HANDLE_EXCEPTION, throwable -> handleThrowable(session, throwable));
      session.registerObjectHandler(this, HANDLE_BODY_PART, body -> handleBodyPart(session, (ByteBuf) body));
      session.registerVoidHandler(this, HANDLE_END, () -> handleEnd(session));
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

   public HttpResponseState addStatusValidator(StatusValidator validator) {
      statusValidators = append(statusValidators, validator, StatusValidator.class);
      return this;
   }

   public HttpResponseState addHeaderValidator(HeaderValidator validator) {
      headerValidators = append(headerValidators, validator, HeaderValidator.class);
      return this;
   }

   public HttpResponseState addBodyValidator(BodyValidator validator) {
      bodyValidators = append(bodyValidators, validator, BodyValidator.class);
      return this;
   }

   public HttpResponseState addStatusExtractor(StatusExtractor extractor) {
      statusExtractors = append(statusExtractors, extractor, StatusExtractor.class);
      return this;
   }

   public HttpResponseState addHeaderExtractor(HeaderExtractor extractor) {
      headerExtractors = append(headerExtractors, extractor, HeaderExtractor.class);
      return this;
   }

   public HttpResponseState addBodyExtractor(BodyExtractor extractor) {
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
}
