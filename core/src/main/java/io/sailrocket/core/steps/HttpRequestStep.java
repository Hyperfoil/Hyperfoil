package io.sailrocket.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.RequestQueue;
import io.sailrocket.api.Step;
import io.sailrocket.api.Session;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.function.SerializableBiConsumer;
import io.sailrocket.function.SerializableFunction;
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
                          SerializableFunction<Session, String> pathGenerator,
                          SerializableFunction<Session, ByteBuf> bodyGenerator,
                          SerializableBiConsumer<Session, HttpRequest> headerAppender,
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
      HttpRequest request = session.httpClientPool().request(method, path, body);
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
      session.currentSequence().statistics(session).incrementRequests();
   }

   @Override
   public void reserve(Session session) {
      handler.reserve(session);
   }

   public static class Builder extends BaseStepBuilder {
      private HttpMethod method;
      private SerializableFunction<Session, String> pathGenerator;
      private SerializableFunction<Session, ByteBuf> bodyGenerator;
      private SerializableBiConsumer<Session, HttpRequest> headerAppender;
      private HttpResponseHandler.Builder handler = new HttpResponseHandler.Builder(this);

      public Builder(BaseSequenceBuilder parent, HttpMethod method) {
         super(parent);
         this.method = method;
      }

      public Builder path(String path) {
         this.pathGenerator = s -> path;
         return this;
      }

      public Builder pathGenerator(SerializableFunction<Session, String> pathGenerator) {
         this.pathGenerator = pathGenerator;
         return this;
      }

      public Builder bodyGenerator(SerializableFunction<Session, ByteBuf> bodyGenerator) {
         this.bodyGenerator = bodyGenerator;
         return this;
      }

      public Builder headerAppender(SerializableBiConsumer<Session, HttpRequest> headerAppender) {
         this.headerAppender = headerAppender;
         return this;
      }

      public HttpResponseHandler.Builder handler() {
         return handler;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new HttpRequestStep(method, pathGenerator, bodyGenerator, headerAppender, handler.build()));
      }
   }
}
