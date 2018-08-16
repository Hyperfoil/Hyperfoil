package io.sailrocket.core.machine;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.DataExtractor;

public class HttpResponseState extends State {
   static final String HANDLE_STATUS = "status";
   static final String HANDLE_EXCEPTION = "exception";
   static final String HANDLE_BODY = "body";
   static final String HANDLE_END = "end";

   private DataExtractor[] extractors;

   public HttpResponseState(String name) {
      super(name);
   }

   private void handleStatus(Session session, int status) {
      log.trace("{} Received status {}", this, status);
   }

   private void handleThrowable(Session session, Throwable throwable) {
      log.trace("{} Received exception {}", this, throwable);
   }

   private void handleBody(Session session, ByteBuf body) {
      // debug only
      byte[] bytes = new byte[body.readableBytes()];
      body.getBytes(body.readerIndex(), bytes, 0, bytes.length);
      log.trace("{} Received body:\n{}", this, new String(bytes));

      if (extractors != null) {
         int dataStartIndex = body.readerIndex();
         for (DataExtractor extractor : extractors) {
            extractor.extractData(body, session);
            body.readerIndex(dataStartIndex);
         }
      }
   }

   @Override
   public void register(Session session) {
      session.registerIntHandler(this, HANDLE_STATUS, status -> handleStatus(session, status));
      session.registerExceptionHandler(this, HANDLE_EXCEPTION, throwable -> handleThrowable(session, throwable));
      session.registerObjectHandler(this, HANDLE_BODY, body -> handleBody(session, (ByteBuf) body));
      session.registerVoidHandler(this, HANDLE_END, () -> session.run());
      if (extractors != null) {
         for (DataExtractor extractor : extractors) {
            if (extractor instanceof ResourceUtilizer) {
               ((ResourceUtilizer) extractor).reserve(session);
            }
         }
      }
   }

   public HttpResponseState addDataExtractor(DataExtractor extractor) {
      if (extractors == null) {
         extractors = new DataExtractor[] { extractor };
      } else {
         extractors = Arrays.copyOf(extractors, extractors.length + 1);
         extractors[extractors.length - 1] = extractor;
      }
      return this;
   }
}
