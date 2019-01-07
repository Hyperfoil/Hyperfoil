package io.sailrocket.core.steps;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.sailrocket.api.config.PairBuilder;
import io.sailrocket.api.config.PartialBuilder;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.connection.HttpRequestWriter;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.util.Util;
import io.sailrocket.function.SerializableBiConsumer;
import io.sailrocket.function.SerializableBiFunction;
import io.sailrocket.function.SerializableFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestStep.class);
   private static final boolean trace = log.isTraceEnabled();

   private final HttpMethod method;
   private final String baseUrl;
   private final SerializableFunction<Session, String> pathGenerator;
   private final SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
   private final SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders;
   private final long timeout;
   private final HttpResponseHandlersImpl handler;

   public HttpRequestStep(HttpMethod method, String baseUrl,
                          SerializableFunction<Session, String> pathGenerator,
                          SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          long timeout, HttpResponseHandlersImpl handler) {
      this.method = method;
      this.baseUrl = baseUrl;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppenders = headerAppenders;
      this.timeout = timeout;
      this.handler = handler;
   }

   @Override
   public boolean invoke(Session session) {
      Request request = session.requestPool().acquire();
      if (request == null) {
         log.warn("#{} Request pool too small; increase it to prevent blocking.", session.uniqueId());
         return false;
      }

      request.start(handler, session.currentSequence());

      HttpConnectionPool connectionPool = session.httpConnectionPool(baseUrl);
      if (!connectionPool.request(request, method, pathGenerator, headerAppenders, bodyGenerator)) {
         log.warn("#{} No HTTP connection in pool, waiting...", session.uniqueId());
         session.requestPool().release(request);
         // TODO: when the phase is finished, max duration is not set and the connection cannot be obtained
         // we'll be waiting here forever. Maybe there should be a (default) timeout to obtain the connection.
         connectionPool.registerWaitingSession(session);
         session.currentSequence().setBlockedTimestamp();
         session.currentSequence().statistics(session).incrementBlockedCount();
         return false;
      }
      long blockedTime = session.currentSequence().getBlockedTime();
      if (blockedTime > 0) {
         session.currentSequence().statistics(session).incrementBlockedTime(blockedTime);
      }
      // Set up timeout only after successful request
      if (timeout > 0) {
         // TODO alloc!
         request.setTimeout(timeout, TimeUnit.MILLISECONDS);
      }

      session.currentSequence().statistics(session).incrementRequests();
      return true;
   }

   @Override
   public void reserve(Session session) {
      handler.reserve(session);
   }

   public static class Builder extends BaseStepBuilder {
      private HttpMethod method;
      private String baseUrl;
      private SerializableFunction<Session, String> pathGenerator;
      private SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
      private List<SerializableBiConsumer<Session, HttpRequestWriter>> headerAppenders = new ArrayList<>();
      private long timeout = Long.MIN_VALUE;
      private HttpResponseHandlersImpl.Builder handler = new HttpResponseHandlersImpl.Builder(this);

      public Builder(BaseSequenceBuilder parent, HttpMethod method) {
         super(parent);
         this.method = method;
      }

      public Builder method(HttpMethod method) {
         this.method = method;
         return this;
      }

      // Methods below allow more brevity in the YAML

      public Builder GET(String path) {
         this.method = HttpMethod.GET;
         return path(path);
      }

      public Builder HEAD(String path) {
         this.method = HttpMethod.HEAD;
         return path(path);
      }

      public Builder POST(String path) {
         this.method = HttpMethod.POST;
         return path(path);
      }

      public Builder PUT(String path) {
         this.method = HttpMethod.PUT;
         return path(path);
      }

      public Builder DELETE(String path) {
         this.method = HttpMethod.DELETE;
         return path(path);
      }

      public Builder OPTIONS(String path) {
         this.method = HttpMethod.OPTIONS;
         return path(path);
      }

      public Builder PATCH(String path) {
         this.method = HttpMethod.PATCH;
         return path(path);
      }

      public Builder TRACE(String path) {
         this.method = HttpMethod.TRACE;
         return path(path);
      }

      public Builder CONNECT(String path) {
         this.method = HttpMethod.CONNECT;
         return path(path);
      }

      public Builder baseUrl(String baseUrl) {
         this.baseUrl = baseUrl;
         return this;
      }

      public Builder path(String path) {
         return pathGenerator(s -> path);
      }

      public Builder pathGenerator(SerializableFunction<Session, String> pathGenerator) {
         if (this.pathGenerator != null) {
            throw new IllegalStateException("Path generator already set.");
         }
         this.pathGenerator = pathGenerator;
         return this;
      }

      public Builder body(String string) {
         ByteBuf buf = Unpooled.wrappedBuffer(string.getBytes(StandardCharsets.UTF_8));
         return bodyGenerator((s, c) -> buf);
      }

      public BodyBuilder body() {
         return new BodyBuilder(this);
      }

      public Builder bodyGenerator(SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator) {
         if (this.bodyGenerator != null) {
            throw new IllegalStateException("Body generator already set.");
         }
         this.bodyGenerator = bodyGenerator;
         return this;
      }

      public Builder headerAppender(SerializableBiConsumer<Session, HttpRequestWriter> headerAppender) {
         headerAppenders.add(headerAppender);
         return this;
      }

      public HeadersBuilder headers() {
         return new HeadersBuilder(this);
      }

      public Builder timeout(long timeout, TimeUnit timeUnit) {
         if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive!");
         } else if (this.timeout != Long.MIN_VALUE) {
            throw new IllegalStateException("Timeout already set!");
         }
         this.timeout = timeUnit.toMillis(timeout);
         return this;
      }

      public Builder timeout(String timeout) {
         return timeout(Util.parseToMillis(timeout), TimeUnit.MILLISECONDS);
      }

      public HttpResponseHandlersImpl.Builder handler() {
         return handler;
      }

      @Override
      public List<Step> build() {
         if (!parent.endSequence().endScenario().endPhase().validateBaseUrl(baseUrl)) {
            String guessedPath = "<unknown path>";
            try {
               guessedPath = pathGenerator.apply(null);
            } catch (Throwable e) {}
            if (baseUrl == null) {
               throw new BenchmarkDefinitionException(String.format("%s to <default route>/%s is invalid as we don't have a default route set.", method, guessedPath));
            } else {
               throw new BenchmarkDefinitionException(String.format("%s to <default route>/%s is invalid - no HTTP configuration defined.", method, baseUrl, guessedPath));
            }
         }
         SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders =
               this.headerAppenders.isEmpty() ? null : this.headerAppenders.toArray(new SerializableBiConsumer[0]);
         return Collections.singletonList(new HttpRequestStep(method, baseUrl, pathGenerator, bodyGenerator, headerAppenders, timeout, handler.build()));
      }
   }

   public static class HeadersBuilder extends PairBuilder.String implements PartialBuilder {
      private final Builder parent;

      public HeadersBuilder(Builder builder) {
         this.parent = builder;
      }

      @Override
      public void accept(java.lang.String header, java.lang.String value) {
         parent.headerAppenders.add((session, writer) -> writer.putHeader(header, value));
      }

      public Builder endHeaders() {
         return parent;
      }

      @Override
      public Object withKey(java.lang.String key) {
         return new PartialHeadersBuilder(parent, key);
      }
   }

   public static class PartialHeadersBuilder {
      private final Builder parent;
      private final String header;

      private PartialHeadersBuilder(Builder parent, String header) {
         this.parent = parent;
         this.header = header;
      }

      public void var(String var) {
         parent.headerAppenders.add((session, writer) -> {
            Object value = session.getObject(var);
            if (value instanceof CharSequence) {
               writer.putHeader(header, (CharSequence) value);
            } else {
               log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), var, value);
            }
         });
      }
   }

   private static class BodyBuilder {
      private final Builder parent;

      private BodyBuilder(Builder parent) {
         this.parent = parent;
      }

      public void var(String var) {
         parent.bodyGenerator((session, connection) -> {
            Object value = session.getObject(var);
            if (value instanceof ByteBuf) {
               return (ByteBuf) value;
            } else if (value instanceof String){
               String str = (String) value;
               // TODO: allocations everywhere but at least not the bytes themselves...
               CharBuffer input = CharBuffer.wrap(str);
               ByteBuf buffer = connection.context().alloc().buffer(str.length());
               ByteBuffer output = buffer.nioBuffer(buffer.writerIndex(), buffer.capacity() - buffer.writerIndex());
               CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
               for (;;) {
                  CoderResult result = encoder.encode(input, output, true);
                  if (result.isError()) {
                     log.error("#{} Cannot encode request body from var {}: {}, string is {}", session.uniqueId(), var, result, str);
                     return null;
                  } else if (result.isUnderflow()) {
                     buffer.writerIndex(output.position());
                     return buffer;
                  } else if (result.isOverflow()) {
                     buffer.capacity(buffer.capacity() * 2);
                     output = buffer.nioBuffer(output.position(), buffer.capacity() - output.position());
                  } else {
                     throw new IllegalStateException();
                  }
               }
            } else {
               log.error("#{} Cannot encode request body from var {}: {}", session.uniqueId(), var, value);
               return null;
            }
         });
      }
   }
}
