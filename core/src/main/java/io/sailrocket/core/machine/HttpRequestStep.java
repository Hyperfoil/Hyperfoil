package io.sailrocket.core.machine;

import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestStep.class);
   private static final boolean trace = log.isTraceEnabled();

   private final HttpMethod method;
   private final Function<Session, String> pathGenerator;
   private final Function<Session, ByteBuf> bodyGenerator;
   private final BiConsumer<Session, HttpRequest> headerAppender;
   private final HttpResponseHandler handler;

   public HttpRequestStep(HttpMethod method,
                          Function<Session, String> pathGenerator,
                          Function<Session, ByteBuf> bodyGenerator,
                          BiConsumer<Session, HttpRequest> headerAppender,
                          HttpResponseHandler handler) {
      this.method = method;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppender = headerAppender;
      this.handler = handler;
   }

   @Override
   public boolean prepare(Session session) {
      RequestQueue.Request request = session.requestQueue().prepare();
      if (request == null) {
         return false;
      } else {
         request.startTime = System.nanoTime();
         request.sequence = session.currentSequence();
         return true;
      }
   }

   @Override
   public void invoke(Session session) {
      ByteBuf body = bodyGenerator == null ? null : bodyGenerator.apply(session);
      String path = pathGenerator.apply(session);
      // TODO alloc!
      HttpRequest request = session.getHttpClientPool().request(method, path, body);
      if (headerAppender != null) {
         headerAppender.accept(session, request);
      }

      // alloc-free below
      HttpResponseHandler.HandlerInstances h = session.getResource(handler);
      request.statusHandler(h.handleStatus);
      request.headerHandler(h.handleHeader);
      request.exceptionHandler(h.handleException);
      request.bodyPartHandler(h.handleBodyPart);
      request.endHandler(h.handleEnd);

      if (trace) {
         log.trace("HTTP {} to {}", method, path);
      }
      request.end();
      session.statistics().requestCount++;
   }

   @Override
   public void reserve(Session session) {
      handler.reserve(session);
   }
}
