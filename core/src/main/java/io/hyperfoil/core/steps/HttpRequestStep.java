package io.hyperfoil.core.steps;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.function.SerializableSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.builders.SimulationBuilder;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.function.SerializableFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestStep extends BaseStep implements ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestStep.class);
   private static final boolean trace = log.isTraceEnabled();

   final HttpMethod method;
   final SerializableFunction<Session, String> baseUrl;
   final SerializableFunction<Session, String> pathGenerator;
   final SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
   final SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders;
   final SerializableBiFunction<String, String, String> statisticsSelector;
   final long timeout;
   final HttpResponseHandlersImpl handler;

   public HttpRequestStep(SerializableSupplier<Sequence> sequence, HttpMethod method,
                          SerializableFunction<Session, String> baseUrl,
                          SerializableFunction<Session, String> pathGenerator,
                          SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          SerializableBiFunction<String, String, String> statisticsSelector,
                          long timeout, HttpResponseHandlersImpl handler) {
      super(sequence);
      this.method = method;
      this.baseUrl = baseUrl;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppenders = headerAppenders;
      this.statisticsSelector = statisticsSelector;
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

      String baseUrl = this.baseUrl == null ? null : this.baseUrl.apply(session);
      String path = pathGenerator.apply(session);
      if (baseUrl == null && (path.startsWith("http://") || path.startsWith("https://"))) {
         baseUrl = session.findBaseUrl(path);
         if (baseUrl == null) {
            log.error("Cannot access {}: no base url configured", path);
            return true;
         }
         path = path.substring(baseUrl.length());
      }
      String statistics = null;
      if (statisticsSelector != null) {
         statistics = statisticsSelector.apply(baseUrl, path);
      }
      if (statistics == null) {
         sequence().name();
      }

      SequenceInstance sequence = session.currentSequence();
      request.start(handler, sequence, session.statistics(statistics));

      HttpConnectionPool connectionPool = session.httpConnectionPool(baseUrl);
      if (!connectionPool.request(request, method, path, headerAppenders, bodyGenerator)) {
         request.setCompleted();
         session.requestPool().release(request);
         // TODO: when the phase is finished, max duration is not set and the connection cannot be obtained
         // we'll be waiting here forever. Maybe there should be a (default) timeout to obtain the connection.
         connectionPool.registerWaitingSession(session);
         sequence.setBlockedTimestamp();
         request.statistics().incrementBlockedCount();
         return false;
      }
      long blockedTime = sequence.getBlockedTime();
      if (blockedTime > 0) {
         request.statistics().incrementBlockedTime(blockedTime);
      }
      // Set up timeout only after successful request
      if (timeout > 0) {
         // TODO alloc!
         request.setTimeout(timeout, TimeUnit.MILLISECONDS);
      } else {
         Simulation simulation = sequence().phase().benchmark().simulation();
         Http http = baseUrl == null ? simulation.defaultHttp() : simulation.http().get(baseUrl);
         long timeout = http.requestTimeout();
         if (timeout > 0) {
            request.setTimeout(timeout, TimeUnit.MILLISECONDS);
         }
      }

      if (trace) {
         log.trace("#{} sent request on {}", session.uniqueId(), request.connection());
      }
      request.statistics().incrementRequests();
      return true;
   }

   @Override
   public void reserve(Session session) {
      handler.reserve(session);
   }

   public static class Builder extends BaseStepBuilder {
      private HttpMethod method;
      private SerializableFunction<Session, String> baseUrl;
      private SerializableFunction<Session, String> pathGenerator;
      private SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
      private List<SerializableBiConsumer<Session, HttpRequestWriter>> headerAppenders = new ArrayList<>();
      private SerializableBiFunction<String, String, String> statisticsSelector;
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
         return method(HttpMethod.GET).path(path);
      }

      public StringGeneratorBuilder GET() {
         return method(HttpMethod.GET).path();
      }

      public Builder HEAD(String path) {
         return method(HttpMethod.HEAD).path(path);
      }

      public StringGeneratorBuilder HEAD() {
         return method(HttpMethod.HEAD).path();
      }

      public Builder POST(String path) {
         return method(HttpMethod.POST).path(path);
      }

      public StringGeneratorBuilder POST() {
         return method(HttpMethod.POST).path();
      }

      public Builder PUT(String path) {
         return method(HttpMethod.PUT).path(path);
      }

      public StringGeneratorBuilder PUT() {
         return method(HttpMethod.PUT).path();
      }

      public Builder DELETE(String path) {
         return method(HttpMethod.DELETE).path(path);
      }

      public StringGeneratorBuilder DELETE() {
         return method(HttpMethod.DELETE).path();
      }

      public Builder OPTIONS(String path) {
         return method(HttpMethod.OPTIONS).path(path);
      }

      public StringGeneratorBuilder OPTIONS() {
         return method(HttpMethod.OPTIONS).path();
      }

      public Builder PATCH(String path) {
         return method(HttpMethod.PATCH).path(path);
      }

      public StringGeneratorBuilder PATCH() {
         return method(HttpMethod.PATCH).path();
      }

      public Builder TRACE(String path) {
         return method(HttpMethod.TRACE).path(path);
      }

      public StringGeneratorBuilder TRACE() {
         return method(HttpMethod.TRACE).path();
      }

      public Builder CONNECT(String path) {
         return method(HttpMethod.CONNECT).path(path);
      }

      public StringGeneratorBuilder CONNECT() {
         return method(HttpMethod.CONNECT).path();
      }

      public Builder baseUrl(String baseUrl) {
         return baseUrl(session -> baseUrl);
      }

      public Builder baseUrl(SerializableFunction<Session, String> baseUrlGenerator) {
         this.baseUrl = baseUrlGenerator;
         return this;
      }

      public StringGeneratorBuilder<HttpRequestStep.Builder> baseUrl() {
         return new StringGeneratorBuilder<>(this, this::baseUrl);
      }

      public Builder path(String path) {
         return pathGenerator(s -> path);
      }

      public StringGeneratorBuilder<HttpRequestStep.Builder> path() {
         return new StringGeneratorBuilder<>(this, this::pathGenerator);
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

      public Builder statistics(SerializableBiFunction<String, String, String> selector) {
         this.statisticsSelector = selector;
         return this;
      }

      public PathStatisticsSelector statistics() {
         PathStatisticsSelector selector = new PathStatisticsSelector();
         this.statisticsSelector = selector;
         return selector;
      }

      public HttpResponseHandlersImpl.Builder handler() {
         return handler;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         SimulationBuilder simulation = parent.endSequence().endScenario().endPhase();
         String guessedBaseUrl = null;
         boolean checkBaseUrl = true;
         try {
            guessedBaseUrl = baseUrl == null ? null : baseUrl.apply(null);
         } catch (Throwable e) {
            checkBaseUrl = false;
         }
         if (checkBaseUrl && !simulation.validateBaseUrl(guessedBaseUrl)) {
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
         return Collections.singletonList(new HttpRequestStep(sequence, method, baseUrl, pathGenerator, bodyGenerator, headerAppenders, statisticsSelector, timeout, handler.build()));
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
      public PartialHeadersBuilder withKey(java.lang.String key) {
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

      public PartialHeadersBuilder var(String var) {
         parent.headerAppenders.add((session, writer) -> {
            Object value = session.getObject(var);
            if (value instanceof CharSequence) {
               writer.putHeader(header, (CharSequence) value);
            } else {
               log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), var, value);
            }
         });
         return this;
      }
   }

   public static class BodyBuilder {
      private final Builder parent;

      private BodyBuilder(Builder parent) {
         this.parent = parent;
      }

      public BodyBuilder var(String var) {
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
               int accumulatedBytes = 0;
               for (;;) {
                  CoderResult result = encoder.encode(input, output, true);
                  if (result.isError()) {
                     log.error("#{} Cannot encode request body from var {}: {}, string is {}", session.uniqueId(), var, result, str);
                     return null;
                  } else if (result.isUnderflow()) {
                     buffer.writerIndex(accumulatedBytes + output.position());
                     return buffer;
                  } else if (result.isOverflow()) {
                     buffer.capacity(buffer.capacity() * 2);
                     int writtenBytes = output.position();
                     accumulatedBytes += writtenBytes;
                     output = buffer.nioBuffer(accumulatedBytes, buffer.capacity() - accumulatedBytes);
                  } else {
                     throw new IllegalStateException();
                  }
               }
            } else {
               log.error("#{} Cannot encode request body from var {}: {}", session.uniqueId(), var, value);
               return null;
            }
         });
         return this;
      }

      public Builder endBody() {
         return parent;
      }
   }

   public static class PathStatisticsSelector implements ListBuilder, SerializableBiFunction<String,String,String> {
      public List<SerializableFunction<String, String>> tests = new ArrayList<>();

      @Override
      public void nextItem(String item) {
         item = item.trim();
         int arrow = item.indexOf("->");
         if (arrow < 0) {
            Pattern pattern = Pattern.compile(item);
            tests.add(path -> pattern.matcher(path).matches() ? path : null);
         } else if (arrow == 0) {
            String replacement = item.substring(2).trim();
            tests.add(path -> replacement);
         } else {
            Pattern pattern = Pattern.compile(item.substring(0, arrow).trim());
            String replacement = item.substring(arrow + 2).trim();
            tests.add(path -> {
               Matcher matcher = pattern.matcher(path);
               if (matcher.matches()) {
                  return matcher.replaceFirst(replacement);
               } else {
                  return null;
               }
            });
         }
      }

      @Override
      public String apply(String baseUrl, String path) {
         String combined = baseUrl != null ? baseUrl + path : path;
         for (SerializableFunction<String, String> test : tests) {
            String result = test.apply(combined);
            if (result != null) {
               return result;
            }
         }
         return null;
      }
   }
}
