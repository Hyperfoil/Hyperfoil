package io.sailrocket.core.machine;

import java.lang.reflect.Array;

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
      log.trace("{} Received status {}", this, status);
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
      log.trace("{} Received header {}: {}", this, header, value);
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
      log.trace("{} Received exception {}", this, throwable);
   }

   private void handleBodyPart(Session session, ByteBuf buf) {
      if (log.isTraceEnabled()) {
         // debug only
         byte[] bytes = new byte[buf.readableBytes()];
         buf.getBytes(buf.readerIndex(), bytes, 0, bytes.length);
         log.trace("{} Received part:\n{}", this, new String(bytes));
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
      session.run();
   }


   @Override
   public void register(Session session) {
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
