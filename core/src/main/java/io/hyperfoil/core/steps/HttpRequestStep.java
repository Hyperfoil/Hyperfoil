package io.hyperfoil.core.steps;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.SLABuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.http.CookieAppender;
import io.hyperfoil.core.http.UserAgentAppender;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.hyperfoil.impl.FutureSupplier;
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
import io.netty.handler.codec.http.HttpHeaderNames;
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
      ResourceUtilizer.reserve(session, baseUrl, pathGenerator, bodyGenerator);
      ResourceUtilizer.reserve(session, headerAppenders);
      handler.reserve(session);
   }

   @Override
   public SLA[] sla() {
      return sla;
   }

   public static class Builder extends BaseStepBuilder {
      private HttpMethod method;
      private StringGeneratorBuilder baseUrl;
      private StringGeneratorBuilder pathGenerator;
      private BodyGeneratorBuilder bodyGenerator;
      private List<SerializableBiConsumer<Session, HttpRequestWriter>> headerAppenders = new ArrayList<>();
      private SerializableBiFunction<String, String, String> statisticsSelector;
      private long timeout = Long.MIN_VALUE;
      private HttpResponseHandlersImpl.Builder handler = new HttpResponseHandlersImpl.Builder(this);
      private boolean sync = true;
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

      public StringGeneratorImplBuilder GET() {
         return method(HttpMethod.GET).path();
      }

      public Builder HEAD(String path) {
         return method(HttpMethod.HEAD).path(path);
      }

      public StringGeneratorImplBuilder HEAD() {
         return method(HttpMethod.HEAD).path();
      }

      public Builder POST(String path) {
         return method(HttpMethod.POST).path(path);
      }

      public StringGeneratorImplBuilder POST() {
         return method(HttpMethod.POST).path();
      }

      public Builder PUT(String path) {
         return method(HttpMethod.PUT).path(path);
      }

      public StringGeneratorImplBuilder PUT() {
         return method(HttpMethod.PUT).path();
      }

      public Builder DELETE(String path) {
         return method(HttpMethod.DELETE).path(path);
      }

      public StringGeneratorImplBuilder DELETE() {
         return method(HttpMethod.DELETE).path();
      }

      public Builder OPTIONS(String path) {
         return method(HttpMethod.OPTIONS).path(path);
      }

      public StringGeneratorImplBuilder OPTIONS() {
         return method(HttpMethod.OPTIONS).path();
      }

      public Builder PATCH(String path) {
         return method(HttpMethod.PATCH).path(path);
      }

      public StringGeneratorImplBuilder PATCH() {
         return method(HttpMethod.PATCH).path();
      }

      public Builder TRACE(String path) {
         return method(HttpMethod.TRACE).path(path);
      }

      public StringGeneratorImplBuilder TRACE() {
         return method(HttpMethod.TRACE).path();
      }

      public Builder CONNECT(String path) {
         return method(HttpMethod.CONNECT).path(path);
      }

      public StringGeneratorImplBuilder CONNECT() {
         return method(HttpMethod.CONNECT).path();
      }

      public Builder baseUrl(String baseUrl) {
         return baseUrl(session -> baseUrl);
      }

      public Builder baseUrl(SerializableFunction<Session, String> baseUrlGenerator) {
         return baseUrl(() -> baseUrlGenerator);
      }

      public StringGeneratorImplBuilder<Builder> baseUrl() {
         StringGeneratorImplBuilder<Builder> builder = new StringGeneratorImplBuilder<>(this, false);
         baseUrl(builder);
         return builder;
      }

      public Builder baseUrl(StringGeneratorBuilder baseUrl) {
         this.baseUrl = baseUrl;
         return this;
      }

      public Builder path(String path) {
         return pathGenerator(s -> path);
      }

      public StringGeneratorImplBuilder<Builder> path() {
         StringGeneratorImplBuilder<Builder> builder = new StringGeneratorImplBuilder<>(this, false);
         pathGenerator(builder);
         return builder;
      }

      public Builder pathGenerator(SerializableFunction<Session, String> pathGenerator) {
         return pathGenerator(() -> pathGenerator);
      }

      public Builder pathGenerator(StringGeneratorBuilder builder) {
         if (this.pathGenerator != null) {
            throw new BenchmarkDefinitionException("Path generator already set.");
         }
         this.pathGenerator = builder;
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
         return bodyGenerator(() -> bodyGenerator);
      }

      public Builder bodyGenerator(BodyGeneratorBuilder bodyGenerator) {
         if (this.bodyGenerator != null) {
            throw new BenchmarkDefinitionException("Body generator already set.");
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
            throw new BenchmarkDefinitionException("Timeout must be positive!");
         } else if (this.timeout != Long.MIN_VALUE) {
            throw new BenchmarkDefinitionException("Timeout already set!");
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
         ErgonomicsBuilder ergonomics = endStep().endSequence().endScenario().endPhase().ergonomics();
         if (ergonomics.repeatCookies()) {
            headerAppender(new CookieAppender());
         }
         if (ergonomics.userAgentFromSession()) {
            headerAppender(new UserAgentAppender());
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
         FutureSupplier<HttpRequestStep> fs = new FutureSupplier<>();

         BenchmarkBuilder simulation = endStep().endSequence().endScenario().endPhase();
         String guessedBaseUrl = null;
         boolean checkBaseUrl = true;
         SerializableFunction<Session, String> baseUrl = this.baseUrl != null ? this.baseUrl.build() : null;
         SerializableFunction<Session, String> pathGenerator = this.pathGenerator != null ? this.pathGenerator.build() : null;
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
               throw new BenchmarkDefinitionException(String.format("%s to <default route>/%s is invalid - no HTTP configuration defined.", method, this.baseUrl, guessedPath));
            }
         }
         @SuppressWarnings("unchecked")
         SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders =
               this.headerAppenders.isEmpty() ? null : this.headerAppenders.toArray(new SerializableBiConsumer[0]);

         SLA[] sla = this.sla != null ? this.sla.build() : null;
         SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator = this.bodyGenerator != null ? this.bodyGenerator.build() : null;

         HttpRequestStep step = new HttpRequestStep(sequence, method, baseUrl, pathGenerator, bodyGenerator, headerAppenders, statisticsSelector, timeout, handler.build(fs), sla);
         fs.set(step);
         return Collections.singletonList(step);
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
         headerAppenders.forEach(newBuilder::headerAppender);
         if (sla != null) {
            newBuilder.sla().readFrom(sla);
         }
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

   public static class HeadersBuilder extends PairBuilder.OfString implements PartialBuilder {
      private final Builder parent;

      public HeadersBuilder(Builder builder) {
         this.parent = builder;
      }

      @Override
      public void accept(String header, String value) {
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
         String myHeader = header;
         parent.headerAppenders.add((session, writer) -> {
            Object value = access.getObject(session);
            if (value instanceof CharSequence) {
               writer.putHeader(myHeader, (CharSequence) value);
            } else {
               log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), access, value);
            }
         });
         return this;
      }
   }

   public interface BodyGeneratorBuilder {
      SerializableBiFunction<Session, Connection, ByteBuf> build();
   }

   public static class BodyBuilder {
      private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
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
         Pattern p = new Pattern(pattern, false);
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

      public FormBuilder form() {
         FormBuilder builder = new FormBuilder();
         parent.headerAppender((session, writer) -> writer.putHeader(HttpHeaderNames.CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED));
         parent.bodyGenerator(builder);
         return builder;
      }

      public Builder endBody() {
         return parent;
      }
   }

   private static class FormBuilder extends PairBuilder.OfString implements BodyGeneratorBuilder, MappingListBuilder<FormInputBuilder> {
      private final ArrayList<FormInputBuilder> inputs = new ArrayList<>();

      @Override
      public FormInputBuilder addItem() {
         FormInputBuilder input = new FormInputBuilder();
         inputs.add(input);
         return input;
      }

      @SuppressWarnings("unchecked")
      @Override
      public SerializableBiFunction<Session, Connection, ByteBuf> build() {
         return new FormGenerator(inputs.stream().map(FormInputBuilder::build).toArray(SerializableBiConsumer[]::new));
      }

      @Override
      public void accept(String name, String value) {
         inputs.add(new FormInputBuilder().name(name).value(value));
      }
   }

   private static class FormGenerator implements SerializableBiFunction<Session, Connection, ByteBuf> {
      private final SerializableBiConsumer<Session, ByteBuf>[] inputs;

      private FormGenerator(SerializableBiConsumer<Session, ByteBuf>[] inputs) {
         this.inputs = inputs;
      }

      @Override
      public ByteBuf apply(Session session, Connection connection) {
         if (inputs.length == 0) {
            return Unpooled.EMPTY_BUFFER;
         }
         ByteBuf buffer = connection.context().alloc().buffer();
         inputs[0].accept(session, buffer);
         for (int i = 1; i < inputs.length; ++i) {
            buffer.ensureWritable(1);
            buffer.writeByte('&');
            inputs[i].accept(session, buffer);
         }
         return buffer;
      }
   }

   public static class FormInputBuilder {
      private String name;
      private String value;
      private String var;
      private String pattern;

      public SerializableBiConsumer<Session, ByteBuf> build() {
         if (value != null && var != null && pattern != null) {
            throw new BenchmarkDefinitionException("Form input: Must set only one of 'value', 'var', 'pattern'");
         } else if (value == null && var == null && pattern == null) {
            throw new BenchmarkDefinitionException("Form input: Must set one of 'value' or 'var' or 'pattern'");
         } else if (name == null) {
            throw new BenchmarkDefinitionException("Form input: 'name' must be set.");
         }
         try {
            byte[] nameBytes = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
            if (value != null) {
               byte[] valueBytes = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
               return (session, buf) -> buf.writeBytes(nameBytes).writeByte('=').writeBytes(valueBytes);
            } else if (var != null) {
               String myVar = this.var; // avoid this capture
               Access access = SessionFactory.access(var);
               return (session, buf) -> {
                  buf.writeBytes(nameBytes).writeByte('=');
                  Session.Var var = access.getVar(session);
                  if (!var.isSet()) {
                     throw new IllegalStateException("Variable " + myVar + " was not set yet!");
                  }
                  if (var instanceof IntVar) {
                     Util.intAsText2byteBuf(var.intValue(), buf);
                  } else if (var instanceof ObjectVar) {
                     Object o = var.objectValue();
                     if (o == null) {
                        // keep it empty
                     } else if (o instanceof byte[]) {
                        buf.writeBytes((byte[]) o);
                     } else {
                        Util.urlEncode(o.toString(), buf);
                     }
                  } else {
                     throw new IllegalStateException();
                  }
               };
            } else {
               return new Pattern(this.pattern, true);
            }
         } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
         }
      }

      public FormInputBuilder name(String name) {
         this.name = name;
         return this;
      }

      public FormInputBuilder value(String value) {
         this.value = value;
         return this;
      }

      public FormInputBuilder var(String var) {
         this.var = var;
         return this;
      }

      public FormInputBuilder pattern(String pattern) {
         this.pattern = pattern;
         return this;
      }
   }
}
