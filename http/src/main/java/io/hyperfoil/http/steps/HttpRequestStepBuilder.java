package io.hyperfoil.http.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.SLABuilder;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.handlers.GzipInflatorProcessor;
import io.hyperfoil.core.handlers.StoreProcessor;
import io.hyperfoil.core.metric.MetricSelector;
import io.hyperfoil.core.metric.ProvidedMetricSelector;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.DelaySessionStartStep;
import io.hyperfoil.core.metric.PathMetricSelector;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.core.util.DoubleIncrementBuilder;
import io.hyperfoil.core.util.Unique;
import io.hyperfoil.impl.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.http.UserAgentAppender;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpErgonomics;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.cookie.CookieAppender;
import io.hyperfoil.http.handlers.FilterHeaderHandler;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Issues a HTTP request and registers handlers for the response.
 */
@MetaInfServices(StepBuilder.class)
@Name("httpRequest")
public class HttpRequestStepBuilder extends BaseStepBuilder<HttpRequestStepBuilder> {
   private static final Logger log = LogManager.getLogger(SendHttpRequestStep.class);

   private int stepId = -1;
   private HttpMethod.Builder method;
   private StringGeneratorBuilder authority;
   private StringGeneratorBuilder endpoint;
   private StringGeneratorBuilder path;
   private BodyGeneratorBuilder body;
   private final List<Supplier<SerializableBiConsumer<Session, HttpRequestWriter>>> headerAppenders = new ArrayList<>();
   private boolean injectHostHeader = true;
   private MetricSelector metricSelector;
   private long timeout = Long.MIN_VALUE;
   private HttpResponseHandlersImpl.Builder handler = new HttpResponseHandlersImpl.Builder(this);
   private boolean sync = true;
   private SLABuilder.ListBuilder<HttpRequestStepBuilder> sla = null;
   private CompensationBuilder compensation;
   private CompressionBuilder compression = new CompressionBuilder(this);

   /**
    * HTTP method used for the request.
    *
    * @param method HTTP method.
    * @return Self.
    */
   public HttpRequestStepBuilder method(HttpMethod method) {
      return method(new HttpMethod.ProvidedBuilder(method));
   }

   public HttpRequestStepBuilder method(HttpMethod.Builder method) {
      this.method = method;
      return this;
   }

   /**
    * Issue HTTP GET request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder GET(String path) {
      return method(HttpMethod.GET).path().pattern(path).end();
   }

   /**
    * Issue HTTP GET request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> GET() {
      return method(HttpMethod.GET).path();
   }

   /**
    * Issue HTTP HEAD request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder HEAD(String path) {
      return method(HttpMethod.HEAD).path().pattern(path).end();
   }

   /**
    * Issue HTTP HEAD request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> HEAD() {
      return method(HttpMethod.HEAD).path();
   }

   /**
    * Issue HTTP POST request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder POST(String path) {
      return method(HttpMethod.POST).path().pattern(path).end();
   }

   /**
    * Issue HTTP POST request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> POST() {
      return method(HttpMethod.POST).path();
   }

   /**
    * Issue HTTP PUT request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder PUT(String path) {
      return method(HttpMethod.PUT).path().pattern(path).end();
   }

   /**
    * Issue HTTP PUT request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> PUT() {
      return method(HttpMethod.PUT).path();
   }

   /**
    * Issue HTTP DELETE request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder DELETE(String path) {
      return method(HttpMethod.DELETE).path().pattern(path).end();
   }

   /**
    * Issue HTTP DELETE request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> DELETE() {
      return method(HttpMethod.DELETE).path();
   }

   /**
    * Issue HTTP OPTIONS request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder OPTIONS(String path) {
      return method(HttpMethod.OPTIONS).path().pattern(path).end();
   }

   /**
    * Issue HTTP OPTIONS request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> OPTIONS() {
      return method(HttpMethod.OPTIONS).path();
   }

   /**
    * Issue HTTP PATCH request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder PATCH(String path) {
      return method(HttpMethod.PATCH).path().pattern(path).end();
   }

   /**
    * Issue HTTP PATCH request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> PATCH() {
      return method(HttpMethod.PATCH).path();
   }

   /**
    * Issue HTTP TRACE request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder TRACE(String path) {
      return method(HttpMethod.TRACE).path().pattern(path).end();
   }

   /**
    * Issue HTTP TRACE request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> TRACE() {
      return method(HttpMethod.TRACE).path();
   }

   /**
    * Issue HTTP CONNECT request to given path. This can be a
    * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
    *
    * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
    * @return Self.
    */
   public HttpRequestStepBuilder CONNECT(String path) {
      return method(HttpMethod.CONNECT).path().pattern(path).end();
   }

   /**
    * Issue HTTP CONNECT request to given path.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> CONNECT() {
      return method(HttpMethod.CONNECT).path();
   }

   /**
    * HTTP authority (host:port) this request should target. Must match one of the entries in <code>http</code> section.
    * The string can use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>.
    *
    * @param authority Host:port.
    * @return Self.
    */
   public HttpRequestStepBuilder authority(String authority) {
      return authority().pattern(authority).end();
   }

   public HttpRequestStepBuilder authority(SerializableFunction<Session, String> authorityGenerator) {
      return authority(() -> authorityGenerator);
   }

   /**
    * HTTP authority (host:port) this request should target. Must match one of the entries in <code>http</code> section.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> authority() {
      StringGeneratorImplBuilder<HttpRequestStepBuilder> builder = new StringGeneratorImplBuilder<>(this);
      authority(builder);
      return builder;
   }

   public HttpRequestStepBuilder authority(StringGeneratorBuilder authority) {
      this.authority = authority;
      return this;
   }

   /**
    * HTTP endpoint this request should target. Must match to the <code>name</code> of the entries in <code>http</code> section.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> endpoint() {
      var builder = new StringGeneratorImplBuilder<>(this);
      this.endpoint = builder;
      return builder;
   }

   /**
    * HTTP path (absolute or relative), including query and fragment.
    * The string can use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>.
    *
    * @return Self.
    */
   public HttpRequestStepBuilder path(String path) {
      return path(() -> new Pattern(path, false));
   }

   /**
    * HTTP path (absolute or relative), including query and fragment.
    *
    * @return Builder.
    */
   public StringGeneratorImplBuilder<HttpRequestStepBuilder> path() {
      StringGeneratorImplBuilder<HttpRequestStepBuilder> builder = new StringGeneratorImplBuilder<>(this);
      path(builder);
      return builder;
   }

   public HttpRequestStepBuilder path(SerializableFunction<Session, String> pathGenerator) {
      return path(() -> pathGenerator);
   }

   public HttpRequestStepBuilder path(StringGeneratorBuilder builder) {
      if (this.path != null) {
         throw new BenchmarkDefinitionException("Path generator already set.");
      }
      this.path = builder;
      return this;
   }

   /**
    * HTTP request body (possibly a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>).
    *
    * @param string Request body.
    * @return Self.
    */
   public HttpRequestStepBuilder body(String string) {
      return body().pattern(string).endBody();
   }

   /**
    * HTTP request body.
    *
    * @return Builder.
    */
   public BodyBuilder body() {
      return new BodyBuilder(this);
   }

   public HttpRequestStepBuilder body(SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      return body(() -> bodyGenerator);
   }

   public HttpRequestStepBuilder body(BodyGeneratorBuilder bodyGenerator) {
      if (this.body != null) {
         throw new BenchmarkDefinitionException("Body generator already set.");
      }
      this.body = bodyGenerator;
      return this;
   }

   BodyGeneratorBuilder bodyBuilder() {
      return body;
   }

   public HttpRequestStepBuilder headerAppender(SerializableBiConsumer<Session, HttpRequestWriter> headerAppender) {
      headerAppenders.add(() -> headerAppender);
      return this;
   }

   public HttpRequestStepBuilder headerAppenders(Collection<? extends Supplier<SerializableBiConsumer<Session, HttpRequestWriter>>> appenders) {
      headerAppenders.addAll(appenders);
      return this;
   }

   List<Supplier<SerializableBiConsumer<Session, HttpRequestWriter>>> headerAppenders() {
      return Collections.unmodifiableList(headerAppenders);
   }

   /**
    * HTTP headers sent in the request.
    *
    * @return Builder.
    */
   public HeadersBuilder headers() {
      return new HeadersBuilder(this);
   }

   public HttpRequestStepBuilder timeout(long timeout, TimeUnit timeUnit) {
      if (timeout <= 0) {
         throw new BenchmarkDefinitionException("Timeout must be positive!");
      } else if (this.timeout != Long.MIN_VALUE) {
         throw new BenchmarkDefinitionException("Timeout already set!");
      }
      this.timeout = timeUnit.toMillis(timeout);
      return this;
   }

   /**
    * Request timeout - after this time the request will be marked as failed and connection will be closed.
    * <p>
    * Defaults to value set globally in <code>http</code> section.
    *
    * @param timeout Timeout.
    * @return Self.
    */
   public HttpRequestStepBuilder timeout(String timeout) {
      return timeout(Util.parseToMillis(timeout), TimeUnit.MILLISECONDS);
   }

   /**
    * Requests statistics will use this metric name.
    *
    * @param name Metric name.
    * @return Self.
    */
   public HttpRequestStepBuilder metric(String name) {
      return metric(new ProvidedMetricSelector(name));
   }

   public HttpRequestStepBuilder metric(MetricSelector selector) {
      this.metricSelector = selector;
      return this;
   }

   /**
    * Allows categorizing request statistics into metrics based on the request path.
    *
    * @return Builder.
    */
   public PathMetricSelector metric() {
      PathMetricSelector selector = new PathMetricSelector();
      this.metricSelector = selector;
      return selector;
   }

   /**
    * HTTP response handlers.
    *
    * @return Builder.
    */
   public HttpResponseHandlersImpl.Builder handler() {
      return handler;
   }

   /**
    * This request is synchronous; execution of the sequence does not continue until the full response
    * is received. If this step is executed from multiple parallel instances of this sequence the progress
    * of all sequences is blocked until there is a request in flight without response.
    * <p>
    * Default is <code>true</code>.
    *
    * @param sync Synchronous?
    * @return Self.
    */
   public HttpRequestStepBuilder sync(boolean sync) {
      this.sync = sync;
      return this;
   }

   /**
    * List of SLAs the requests are subject to.
    *
    * @return Builder.
    */
   public SLABuilder.ListBuilder<HttpRequestStepBuilder> sla() {
      if (sla == null) {
         sla = new SLABuilder.ListBuilder<>(this);
      }
      return sla;
   }

   /**
    * Configures additional metric compensated for coordinated omission.
    *
    * @return Builder.
    */
   public CompensationBuilder compensation() {
      return this.compensation = new CompensationBuilder(this);
   }

   /**
    * Request server to respond with compressed entity using specified content encoding.
    *
    * @param encoding Encoding. Currently supports only <code>gzip</code>.
    * @return Self.
    */
   public HttpRequestStepBuilder compression(String encoding) {
      compression().encoding(encoding);
      return this;
   }

   /**
    * Configure response compression.
    *
    * @return Builder.
    */
   public CompressionBuilder compression() {
      return compression;
   }

   @Override
   public int id() {
      assert stepId >= 0;
      return stepId;
   }

   @Override
   public void prepareBuild() {
      stepId = StatisticsStep.nextId();
      Locator locator = Locator.current();

      HttpErgonomics ergonomics = locator.benchmark().plugin(HttpPluginBuilder.class).ergonomics();
      if (ergonomics.repeatCookies()) {
         headerAppender(new CookieAppender());
      }
      if (ergonomics.userAgentFromSession()) {
         headerAppender(new UserAgentAppender());
      }
      BeforeSyncRequestStep beforeSyncRequestStep = null;
      if (sync) {
         // We need to perform this in prepareBuild() because the completion handlers must not be modified
         // in the build() method. Alternative would be caching the key and returning the wrapping steps
         // in the list.
         beforeSyncRequestStep = new BeforeSyncRequestStep();
         locator.sequence().insertBefore(locator).step(beforeSyncRequestStep);
         handler.onCompletion(new ReleaseSyncAction(beforeSyncRequestStep));
         // AfterSyncRequestStep must be inserted only after all handlers are prepared
      }
      if (metricSelector == null) {
         String sequenceName = Locator.current().sequence().name();
         metricSelector = new ProvidedMetricSelector(sequenceName);
      }

      if (compensation != null) {
         compensation.prepareBuild();
      }
      compression.prepareBuild();
      handler.prepareBuild();

      // We insert the AfterSyncRequestStep only after preparing all the handlers to ensure
      // that this is added immediately after the SendHttpRequestStep, in case some of the handlers
      // insert their own steps after current step. (We could do this in the build() method, too).
      if (sync) {
         locator.sequence().insertAfter(locator).step(new AfterSyncRequestStep(beforeSyncRequestStep));
      }
   }

   @Override
   public List<Step> build() {
      String guessedAuthority = null;
      boolean checkAuthority = true;
      HttpPluginBuilder httpPlugin = Locator.current().benchmark().plugin(HttpPluginBuilder.class);
      SerializableFunction<Session, String> authority = this.authority != null ? this.authority.build() : null;
      SerializableFunction<Session, String> endpoint = this.endpoint != null ? this.endpoint.build() : null;
      HttpBuilder http = null;
      if (authority != null && endpoint != null) {
         throw new BenchmarkDefinitionException("You have set both endpoint (abstract name) and authority (host:port) as the request target; use only one.");
      }
      if (endpoint != null) {
         checkAuthority = false;
         try {
            String guessedEndpoint = endpoint.apply(null);
            if (guessedEndpoint != null) {
               if (!httpPlugin.validateEndpoint(guessedEndpoint)) {
                  throw new BenchmarkDefinitionException("There is no HTTP endpoint '" + guessedEndpoint + "'");
               }
               http = httpPlugin.getHttpByName(guessedEndpoint);
            }
         } catch (Throwable e) {
            // errors are allowed here
         }
      }
      SerializableFunction<Session, String> pathGenerator = this.path != null ? this.path.build() : null;
      try {
         guessedAuthority = authority == null ? null : authority.apply(null);
      } catch (Throwable e) {
         checkAuthority = false;
      }
      if (checkAuthority) {
         http = httpPlugin.getHttp(guessedAuthority);
      }
      if (checkAuthority && !httpPlugin.validateAuthority(guessedAuthority)) {
         String guessedPath = "<unknown path>";
         try {
            if (pathGenerator != null) {
               guessedPath = pathGenerator.apply(null);
            }
         } catch (Throwable e) {
            // errors are allowed here
         }
         if (authority == null) {
            throw new BenchmarkDefinitionException(String.format("%s to <default route>%s is invalid as we don't have a default route set.", method, guessedPath));
         } else {
            throw new BenchmarkDefinitionException(String.format("%s to %s%s is invalid - no HTTP configuration defined.", method, guessedAuthority, guessedPath));
         }
      }
      if (sla == null && http != null && (http.connectionStrategy() == ConnectionStrategy.OPEN_ON_REQUEST || http.connectionStrategy() == ConnectionStrategy.ALWAYS_NEW)) {
         this.sla = new SLABuilder.ListBuilder<>(this).addItem().blockedRatio(1.01).endSLA();
      }
      @SuppressWarnings("unchecked")
      SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders =
            this.headerAppenders.isEmpty() ? null :
                  this.headerAppenders.stream().map(Supplier::get).toArray(SerializableBiConsumer[]::new);

      SLA[] sla = this.sla != null ? this.sla.build() : SLABuilder.DEFAULT;
      SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator = this.body != null ? this.body.build() : null;

      HttpRequestContext.Key contextKey = new HttpRequestContext.Key();
      PrepareHttpRequestStep prepare = new PrepareHttpRequestStep(stepId, contextKey, method.build(), endpoint, authority, pathGenerator, metricSelector, handler.build());
      SendHttpRequestStep step = new SendHttpRequestStep(stepId, contextKey, bodyGenerator, headerAppenders, injectHostHeader, timeout, sla);
      return Arrays.asList(prepare, step);
   }

   public enum CompressionType {
      /**
       * Use <code>Accept-Encoding</code> in request and expect <code>Content-Encoding</code> in response.
       */
      CONTENT_ENCODING,
      /**
       * Use <code>TE</code> in request and expect <code>Transfer-Encoding</code> in response.
       */
      TRANSFER_ENCODING
   }

   public interface BodyGeneratorBuilder extends BuilderBase<BodyGeneratorBuilder> {
      SerializableBiFunction<Session, Connection, ByteBuf> build();
   }

   private static class PrefixMetricSelector implements SerializableBiFunction<String, String, String> {
      private final String prefix;
      private final SerializableBiFunction<String, String, String> delegate;

      private PrefixMetricSelector(String prefix, SerializableBiFunction<String, String, String> delegate) {
         this.prefix = prefix;
         this.delegate = delegate;
      }

      @Override
      public String apply(String authority, String path) {
         return prefix + delegate.apply(authority, path);
      }
   }

   private static class ReleaseSyncAction implements Action {
      @Visitor.Ignore
      private final BeforeSyncRequestStep beforeSyncRequestStep;

      ReleaseSyncAction(BeforeSyncRequestStep beforeSyncRequestStep) {
         this.beforeSyncRequestStep = beforeSyncRequestStep;
      }

      @Override
      public void run(Session s) {
         s.getResource(beforeSyncRequestStep).set(s.currentSequence().index());
      }
   }

   public static class HeadersBuilder extends PairBuilder.OfString implements PartialBuilder {
      private final HttpRequestStepBuilder parent;

      public HeadersBuilder(HttpRequestStepBuilder builder) {
         this.parent = builder;
      }

      public HeadersBuilder header(CharSequence header, CharSequence value) {
         warnIfUsingHostHeader(header);
         parent.headerAppender(new StaticHeaderWriter(header, value));
         return this;
      }

      /**
       * Use header name (e.g. <code>Content-Type</code>) as key and value (possibly a
       * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>).
       */
      @Override
      public void accept(String header, String value) {
         withKey(header).pattern(value);
      }

      public HttpRequestStepBuilder endHeaders() {
         return parent;
      }

      /**
       * Use header name (e.g. <code>Content-Type</code>) as key and specify value in the mapping.
       */
      @Override
      public PartialHeadersBuilder withKey(String key) {
         warnIfUsingHostHeader(key);
         return new PartialHeadersBuilder(this, key);
      }

      private void warnIfUsingHostHeader(CharSequence key) {
         if (key.toString().equalsIgnoreCase("host")) {
            log.warn("Setting `host` header explicitly is not recommended. Use the HTTP host and adjust actual target using `addresses` property.");
            parent.injectHostHeader = false;
         }
      }
   }

   private static class StaticHeaderWriter implements SerializableBiConsumer<Session, HttpRequestWriter> {
      private final CharSequence header;
      private final CharSequence value;

      private StaticHeaderWriter(CharSequence header, CharSequence value) {
         this.header = header;
         this.value = value;
      }

      @Override
      public void accept(Session session, HttpRequestWriter writer) {
         writer.putHeader(header, value);
      }
   }

   /**
    * Specifies value that should be sent in headers.
    */
   public static class PartialHeadersBuilder implements InitFromParam<PartialHeadersBuilder> {
      private final HeadersBuilder parent;
      private final String header;
      private boolean added;

      private PartialHeadersBuilder(HeadersBuilder parent, String header) {
         this.parent = parent;
         this.header = header;
      }

      /**
       * @param param The value. This can be a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
       * @return Self.
       */
      @Override
      public PartialHeadersBuilder init(String param) {
         return pattern(param);
      }

      /**
       * Load header value from session variable.
       *
       * @param var Variable name.
       * @return Self.
       */
      public PartialHeadersBuilder fromVar(String var) {
         ensureOnce();
         parent.parent.headerAppenders.add(() -> new FromVarHeaderWriter(header, SessionFactory.readAccess(var)));
         return this;
      }

      /**
       * Load header value using a <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
       *
       * @param patternString Pattern to be encoded, e.g. <code>foo${variable}bar${another-variable}</code>
       * @return Builder.
       */
      public PartialHeadersBuilder pattern(String patternString) {
         ensureOnce();
         parent.parent.headerAppenders.add(() -> new PartialHeadersBuilder.PatternHeaderWriter(header, new Pattern(patternString, false)));
         return this;
      }

      private void ensureOnce() {
         if (added) {
            throw new BenchmarkDefinitionException("Trying to add header " + header + " twice. Use only one of: fromVar, pattern");
         }
         added = true;
      }

      public HeadersBuilder end() {
         return parent;
      }

      private static class PatternHeaderWriter implements SerializableBiConsumer<Session, HttpRequestWriter> {
         private final String header;
         private final Pattern pattern;

         PatternHeaderWriter(String header, Pattern pattern) {
            this.header = header;
            this.pattern = pattern;
         }

         @Override
         public void accept(Session session, HttpRequestWriter writer) {
            writer.putHeader(header, pattern.apply(session));
         }
      }
   }

   private static class FromVarHeaderWriter implements SerializableBiConsumer<Session, HttpRequestWriter> {
      private final CharSequence header;
      private final ReadAccess fromVar;

      FromVarHeaderWriter(CharSequence header, ReadAccess fromVar) {
         this.fromVar = fromVar;
         this.header = header;
      }

      @Override
      public void accept(Session session, HttpRequestWriter writer) {
         Object value = fromVar.getObject(session);
         if (value instanceof CharSequence) {
            writer.putHeader(header, (CharSequence) value);
         } else {
            log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), fromVar, value);
         }
      }
   }

   public static class CompensationBuilder {
      private static final String DELAY_SESSION_START = "__delay-session-start";
      private final HttpRequestStepBuilder parent;
      public SerializableBiFunction<String, String, String> metricSelector;
      public double targetRate;
      public double targetRateIncrement;
      private DoubleIncrementBuilder targetRateBuilder;

      public CompensationBuilder(HttpRequestStepBuilder parent) {
         this.parent = parent;
      }

      /**
       * Desired rate of new virtual users per second. This is similar to <code>constantRate.usersPerSec</code>
       * phase settings but works closer to legacy benchmark drivers by fixing the concurrency.
       *
       * @param targetRate Used for calculating the period of each virtual user.
       * @return Self.
       */
      public CompensationBuilder targetRate(double targetRate) {
         this.targetRate = targetRate;
         return this;
      }

      /**
       * Desired rate of new virtual users per second. This is similar to <code>constantRate.usersPerSec</code>
       * phase settings but works closer to legacy benchmark drivers by fixing the concurrency.
       *
       * @return Builder.
       */
      public DoubleIncrementBuilder targetRate() {
         return targetRateBuilder = new DoubleIncrementBuilder((base, inc) -> {
            this.targetRate = base;
            this.targetRateIncrement = inc;
         });
      }

      /**
       * Metric name for the compensated results.
       *
       * @param name Metric name.
       * @return Self.
       */
      public CompensationBuilder metric(String name) {
         this.metricSelector = new ProvidedMetricSelector(name);
         return this;
      }

      /**
       * Configure a custom metric for the compensated results.
       *
       * @return Builder.
       */
      public PathMetricSelector metric() {
         PathMetricSelector metricSelector = new PathMetricSelector();
         this.metricSelector = metricSelector;
         return metricSelector;
      }

      public void prepareBuild() {
         if (targetRateBuilder != null) {
            targetRateBuilder.apply();
         }
         ScenarioBuilder scenario = Locator.current().scenario();
         PhaseBuilder<?> phaseBuilder = scenario.endScenario();
         if (!(phaseBuilder instanceof PhaseBuilder.Always)) {
            throw new BenchmarkDefinitionException("delaySessionStart step makes sense only in phase type 'always'");
         }

         if (!scenario.hasSequence(DELAY_SESSION_START)) {
            List<SequenceBuilder> prev = scenario.resetInitialSequences();
            scenario.initialSequence(DELAY_SESSION_START)
                  .step(new DelaySessionStartStep(prev.stream().map(SequenceBuilder::name).toArray(String[]::new), targetRate, targetRateIncrement, true));
         } else {
            log.warn("Scenario for phase {} contains multiple compensating HTTP requests: make sure that all use the same rate.", phaseBuilder.name());
         }
         parent.handler.onCompletion(new CompensatedResponseRecorder.Builder().metric(metricSelector));
      }

      public HttpRequestStepBuilder end() {
         return parent;
      }
   }

   public static class CompensatedResponseRecorder implements Action {
      private final int stepId;
      private final SerializableBiFunction<String, String, String> metricSelector;

      public CompensatedResponseRecorder(int stepId, SerializableBiFunction<String, String, String> metricSelector) {
         this.stepId = stepId;
         this.metricSelector = metricSelector;
      }

      @Override
      public void run(Session session) {
         HttpRequest request = HttpRequest.ensure(session.currentRequest());
         if (request == null) {
            return;
         }
         String metric = metricSelector.apply(request.authority, request.path);
         Statistics statistics = session.statistics(stepId, metric);

         DelaySessionStartStep.Holder holder = session.getResource(DelaySessionStartStep.KEY);
         long startTimeMs = holder.lastStartTime();
         statistics.incrementRequests(startTimeMs);
         if (request.cacheControl.wasCached) {
            HttpStats.addCacheHit(statistics, startTimeMs);
         } else {
            long now = System.currentTimeMillis();
            log.trace("#{} Session start {}, now {}, diff {}", session.uniqueId(), startTimeMs, now, now - startTimeMs);
            statistics.recordResponse(startTimeMs, TimeUnit.MILLISECONDS.toNanos(now - startTimeMs));
         }
      }

      public static class Builder implements Action.Builder {
         private SerializableBiFunction<String, String, String> metricSelector;

         @Override
         public Action build() {
            HttpRequestStepBuilder stepBuilder = (HttpRequestStepBuilder) Locator.current().step();
            SerializableBiFunction<String, String, String> metricSelector = this.metricSelector;
            if (metricSelector == null) {
               metricSelector = new PrefixMetricSelector("compensated-", stepBuilder.metricSelector);
            }
            return new CompensatedResponseRecorder(stepBuilder.id(), metricSelector);
         }

         public CompensatedResponseRecorder.Builder metric(SerializableBiFunction<String, String, String> metricSelector) {
            this.metricSelector = metricSelector;
            return this;
         }
      }
   }

   public static class CompressionBuilder implements BuilderBase<CompressionBuilder> {
      private final HttpRequestStepBuilder parent;
      private String encoding;
      private CompressionType type = CompressionType.CONTENT_ENCODING;

      public CompressionBuilder() {
         this(null);
      }

      public CompressionBuilder(HttpRequestStepBuilder parent) {
         this.parent = parent;
      }

      /**
       * Encoding used for <code>Accept-Encoding</code>/<code>TE</code> header. The only currently supported is <code>gzip</code>.
       *
       * @param encoding Content encoding.
       * @return Self.
       */
      public CompressionBuilder encoding(String encoding) {
         this.encoding = encoding;
         return this;
      }

      /**
       * Type of compression (resource vs. transfer based).
       *
       * @param type Compression type.
       * @return Self.
       */
      public CompressionBuilder type(CompressionType type) {
         this.type = type;
         return this;
      }

      public HttpRequestStepBuilder end() {
         return parent;
      }

      public void prepareBuild() {
         if (encoding == null) {
            // ignore
         } else if (!encoding.equalsIgnoreCase("gzip")) {
            throw new BenchmarkDefinitionException("The only supported compression encoding is 'gzip'");
         } else {
            Unique encoding = new Unique(Locator.current().sequence().rootSequence().concurrency() > 0);
            AsciiString expectedHeader;
            if (type == CompressionType.CONTENT_ENCODING) {
               parent.headerAppender(new StaticHeaderWriter(HttpHeaderNames.ACCEPT_ENCODING.toString(), this.encoding));
               expectedHeader = HttpHeaderNames.CONTENT_ENCODING;
            } else if (type == CompressionType.TRANSFER_ENCODING) {
               parent.headerAppender(new StaticHeaderWriter(HttpHeaderNames.TE, this.encoding));
               expectedHeader = HttpHeaderNames.TRANSFER_ENCODING;
            } else {
               throw new BenchmarkDefinitionException("Unexpected compression type: " + type);
            }
            parent.handler.header(new FilterHeaderHandler.Builder()
                  .header().equalTo(expectedHeader.toString()).end()
                  .processor(new StoreProcessor.Builder().toVar(encoding)));
            parent.handler.wrapBodyHandlers(handlers -> new GzipInflatorProcessor.Builder().processors(handlers).encodingVar(encoding));
         }
      }
   }
}
