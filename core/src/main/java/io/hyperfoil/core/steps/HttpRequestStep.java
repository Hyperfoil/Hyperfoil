package io.hyperfoil.core.steps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.SLABuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.http.CookieAppender;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.function.SerializableFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestStep extends BaseStep implements ResourceUtilizer, SLA.Provider {
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
   final SLA[] sla;

   public HttpRequestStep(SerializableSupplier<Sequence> sequence, HttpMethod method,
                          SerializableFunction<Session, String> baseUrl,
                          SerializableFunction<Session, String> pathGenerator,
                          SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          SerializableBiFunction<String, String, String> statisticsSelector,
                          long timeout, HttpResponseHandlersImpl handler, SLA[] sla) {
      super(sequence);
      this.method = method;
      this.baseUrl = baseUrl;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppenders = headerAppenders;
      this.statisticsSelector = statisticsSelector;
      this.timeout = timeout;
      this.handler = handler;
      this.sla = sla;
   }

   @Override
   public boolean invoke(Session session) {
      HttpRequest request = session.httpRequestPool().acquire();
      if (request == null) {
         log.warn("#{} Request pool too small; increase it to prevent blocking.", session.uniqueId());
         return false;
      }

      String baseUrl = this.baseUrl == null ? null : this.baseUrl.apply(session);
      String path = pathGenerator.apply(session);
      if (baseUrl == null && (path.startsWith("http://") || path.startsWith("https://"))) {
         for (String url : session.httpDestinations().baseUrls()) {
            if (path.startsWith(url)) {
               baseUrl = url;
            }
         }
         if (baseUrl == null) {
            log.error("Cannot access {}: no base url configured", path);
            return true;
         }
         path = path.substring(baseUrl.length());
      }
      String statsName = null;
      if (statisticsSelector != null) {
         statsName = statisticsSelector.apply(baseUrl, path);
      }
      if (statsName == null) {
         statsName = sequence().name();
      }
      Statistics statistics = session.statistics(id(), statsName);
      SequenceInstance sequence = session.currentSequence();
      request.baseUrl = baseUrl;
      request.method = method;
      request.path = path;
      request.start(handler, sequence, statistics);

      HttpConnectionPool connectionPool = session.httpDestinations().getConnectionPool(baseUrl);
      if (!connectionPool.request(request, headerAppenders, bodyGenerator)) {
         request.setCompleted();
         session.httpRequestPool().release(request);
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
         Benchmark benchmark = session.phase().benchmark();
         Http http = baseUrl == null ? benchmark.defaultHttp() : benchmark.http().get(baseUrl);
         long timeout = http.requestTimeout();
         if (timeout > 0) {
            request.setTimeout(timeout, TimeUnit.MILLISECONDS);
         }
      }

      if (trace) {
         log.trace("#{} sent to {} request on {}", session.uniqueId(), path, request.connection());
      }
      request.statistics().incrementRequests();
      return true;
   }

   @Override
   public void reserve(Session session) {
      handler.reserve(session);
   }

   @Override
   public SLA[] sla() {
      return sla;
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
      private boolean sync = false;
      private SLABuilder.ListBuilder<Builder> sla = null;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
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
         return timeout(io.hyperfoil.util.Util.parseToMillis(timeout), TimeUnit.MILLISECONDS);
      }

      public Builder statistics(String name) {
         return statistics((baseUrl, path) -> name);
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

      public Builder sync(boolean sync) {
         this.sync = sync;
         return this;
      }

      public SLABuilder.ListBuilder<Builder> sla() {
         if (sla == null) {
            sla = new SLABuilder.ListBuilder<>(this);
         }
         return sla;
      }

      @Override
      public void prepareBuild() {
         if (endStep().endSequence().endScenario().endPhase().ergonomics().repeatCookies()) {
            headerAppender(new CookieAppender());
         }
         if (sync) {
            String var = String.format("%s_sync_%08x", endStep().name(), ThreadLocalRandom.current().nextInt());
            Access access = SessionFactory.access(var);
            endStep().insertBefore(this).step(new SyncRequestIncrementStep(var));
            handler.onCompletion(s -> access.addToInt(s, -1));
            endStep().insertAfter(this).step(new AwaitIntStep(var, x -> x == 0));
         }
         handler.prepareBuild();
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         BenchmarkBuilder simulation = endStep().endSequence().endScenario().endPhase();
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

         SLA[] sla = this.sla != null ? this.sla.build() : null;
         return Collections.singletonList(new HttpRequestStep(sequence, method, baseUrl, pathGenerator, bodyGenerator, headerAppenders, statisticsSelector, timeout, handler.build(), sla));
      }

      @Override
      public void addCopyTo(BaseSequenceBuilder newParent) {
         Builder newBuilder = new Builder(newParent)
               .method(method)
               .baseUrl(baseUrl)
               .pathGenerator(pathGenerator)
               .bodyGenerator(bodyGenerator)
               .statistics(statisticsSelector)
               .sync(sync);
         if (timeout > 0) {
            newBuilder.timeout(timeout, TimeUnit.MILLISECONDS);
         }
         newBuilder.handler().readFrom(handler);
      }

      @Override
      public boolean canBeLocated() {
         return true;
      }
   }

   private static class SyncRequestIncrementStep implements Step, ResourceUtilizer {
      private final Access var;

      public SyncRequestIncrementStep(String var) {
         this.var = SessionFactory.access(var);
      }

      @Override
      public boolean invoke(Session s) {
         if (var.isSet(s)) {
            if (var.getInt(s) == 0) {
               s.fail(new IllegalStateException("Synchronous HTTP request executed multiple times."));
            } else {
               var.addToInt(s, 1);
            }
         } else {
            var.setInt(s, 1);
         }
         return true;
      }

      @Override
      public void reserve(Session session) {
         var.declareInt(session);
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
         Access access = SessionFactory.access(var);
         parent.headerAppenders.add((session, writer) -> {
            Object value = access.getObject(session);
            if (value instanceof CharSequence) {
               writer.putHeader(header, (CharSequence) value);
            } else {
               log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), access, value);
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
         Access access = SessionFactory.access(var);
         parent.bodyGenerator((session, connection) -> {
            Object value = access.getObject(session);
            if (value instanceof ByteBuf) {
               return (ByteBuf) value;
            } else if (value instanceof String){
               String str = (String) value;
               return Util.string2byteBuf(str, connection.context().alloc().buffer(str.length()));

            } else {
               log.error("#{} Cannot encode request body from var {}: {}", session.uniqueId(), access, value);
               return null;
            }
         });
         return this;
      }

      public BodyBuilder pattern(String pattern) {
         Pattern p = new Pattern(pattern);
         parent.bodyGenerator((session, connection) -> {
            String str = p.apply(session);
            return Util.string2byteBuf(str, connection.context().alloc().buffer(str.length()));
         });
         return this;
      }

      public BodyBuilder text(String text) {
         byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
         parent.bodyGenerator(((session, connection) -> Unpooled.wrappedBuffer(bytes)));
         return this;
      }

      public Builder endBody() {
         return parent;
      }
   }

}
