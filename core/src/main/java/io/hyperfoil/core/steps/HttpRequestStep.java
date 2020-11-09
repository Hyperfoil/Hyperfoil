package io.hyperfoil.core.steps;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Http;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.SLABuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session.VarType;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.generators.HttpMethodBuilder;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.generators.StringGeneratorBuilder;
import io.hyperfoil.core.generators.StringGeneratorImplBuilder;
import io.hyperfoil.core.http.CookieAppender;
import io.hyperfoil.core.http.HttpUtil;
import io.hyperfoil.core.http.UserAgentAppender;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.BitSetResource;
import io.hyperfoil.core.util.ConstantBytesGenerator;
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
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.function.SerializableFunction;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestStep extends StatisticsStep implements ResourceUtilizer, SLA.Provider {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestStep.class);
   private static final boolean trace = log.isTraceEnabled();

   final SerializableFunction<Session, HttpMethod> method;
   final SerializableFunction<Session, String> authority;
   final SerializableFunction<Session, String> pathGenerator;
   final SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
   final SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders;
   private final boolean injectHostHeader;
   final SerializableBiFunction<String, String, String> metricSelector;
   final long timeout;
   final HttpResponseHandlersImpl handler;
   final SLA[] sla;

   public HttpRequestStep(int stepId,
                          SerializableFunction<Session, HttpMethod> method,
                          SerializableFunction<Session, String> authority,
                          SerializableFunction<Session, String> pathGenerator,
                          SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator,
                          SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                          boolean injectHostHeader,
                          SerializableBiFunction<String, String, String> metricSelector,
                          long timeout, HttpResponseHandlersImpl handler, SLA[] sla) {
      super(stepId);
      this.method = method;
      this.authority = authority;
      this.pathGenerator = pathGenerator;
      this.bodyGenerator = bodyGenerator;
      this.headerAppenders = headerAppenders;
      this.injectHostHeader = injectHostHeader;
      this.metricSelector = metricSelector;
      this.timeout = timeout;
      this.handler = handler;
      this.sla = sla;
   }

   @Override
   public boolean invoke(Session session) {
      SequenceInstance sequence = session.currentSequence();
      HttpRequest request = session.httpRequestPool().acquire();
      if (request == null) {
         log.warn("#{} Request pool too small; increase it to prevent blocking.", session.uniqueId());
         return false;
      }
      request.method = method.apply(session);
      HttpConnectionPool connectionPool;
      String path;
      String authority;
      try {
         authority = this.authority == null ? null : this.authority.apply(session);
         path = pathGenerator.apply(session);
         boolean isHttp;
         if (authority == null && ((isHttp = path.startsWith(HttpUtil.HTTP_PREFIX)) || path.startsWith(HttpUtil.HTTPS_PREFIX))) {
            for (String hostPort : session.httpDestinations().authorities()) {
               // TODO: fixme: this does consider default port match
               if (path.regionMatches(prefixLength(isHttp), hostPort, 0, hostPort.length())) {
                  authority = hostPort;
               }
            }
            if (authority == null) {
               log.error("Cannot access {}: no base url configured", path);
               return true;
            }
            path = path.substring(prefixLength(isHttp) + authority.length());
         }
         String metric = metricSelector.apply(authority, path);
         Statistics statistics = session.statistics(id(), metric);
         request.path = path;
         request.start(handler, sequence, statistics);

         connectionPool = session.httpDestinations().getConnectionPool(authority);
         if (connectionPool == null) {
            session.fail(new BenchmarkExecutionException("There is no connection pool with authority '" + authority +
                  "', available pools are: " + Arrays.asList(session.httpDestinations().authorities())));
            return false;
         }
         request.authority = authority == null ? connectionPool.clientPool().authority() : authority;
      } catch (Throwable t) {
         // If any error happens we still need to release the request
         // The request is either IDLE or RUNNING - we need to make it running, otherwise we could not release it
         if (!request.isRunning()) {
            request.start(null, null);
         }
         request.setCompleted();
         request.release();
         throw t;
      }
      if (!connectionPool.request(request, headerAppenders, injectHostHeader, bodyGenerator, false)) {
         request.setCompleted();
         request.release();
         // TODO: when the phase is finished, max duration is not set and the connection cannot be obtained
         // we'll be waiting here forever. Maybe there should be a (default) timeout to obtain the connection.
         connectionPool.registerWaitingSession(session);
         sequence.setBlockedTimestamp();
         request.statistics().incrementBlockedCount(request.startTimestampMillis());
         return false;
      }
      long blockedTime = sequence.getBlockedTime();
      if (blockedTime > 0) {
         request.statistics().incrementBlockedTime(request.startTimestampMillis(), blockedTime);
      }
      if (request.isCompleted()) {
         // When the request handlers call Session.stop() due to a failure it does not make sense to continue
         request.release();
         return true;
      }
      // Set up timeout only after successful request
      if (timeout > 0) {
         // TODO alloc!
         request.setTimeout(timeout, TimeUnit.MILLISECONDS);
      } else {
         Benchmark benchmark = session.phase().benchmark();
         Http http = authority == null ? benchmark.defaultHttp() : benchmark.http().get(authority);
         long timeout = http.requestTimeout();
         if (timeout > 0) {
            request.setTimeout(timeout, TimeUnit.MILLISECONDS);
         }
      }

      if (trace) {
         log.trace("#{} sent to {} request on {}", session.uniqueId(), path, request.connection());
      }
      request.statistics().incrementRequests(request.startTimestampMillis());
      return true;
   }

   private int prefixLength(boolean isHttp) {
      return isHttp ? HttpUtil.HTTP_PREFIX.length() : HttpUtil.HTTPS_PREFIX.length();
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, authority, pathGenerator, bodyGenerator);
      ResourceUtilizer.reserve(session, (Object[]) headerAppenders);
      handler.reserve(session);
   }

   @Override
   public SLA[] sla() {
      return sla;
   }

   /**
    * Issues a HTTP request and registers handlers for the response.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("httpRequest")
   public static class Builder extends BaseStepBuilder<Builder> {
      private int stepId = -1;
      private HttpMethodBuilder method;
      private StringGeneratorBuilder authority;
      private StringGeneratorBuilder path;
      private BodyGeneratorBuilder body;
      private final List<Supplier<SerializableBiConsumer<Session, HttpRequestWriter>>> headerAppenders = new ArrayList<>();
      private boolean injectHostHeader = true;
      private SerializableBiFunction<String, String, String> metricSelector;
      private long timeout = Long.MIN_VALUE;
      private final HttpResponseHandlersImpl.Builder handler = new HttpResponseHandlersImpl.Builder(this);
      private boolean sync = true;
      private SLABuilder.ListBuilder<Builder> sla = null;

      /**
       * HTTP method used for the request.
       *
       * @param method HTTP method.
       * @return Self.
       */
      public Builder method(HttpMethod method) {
         return method(() -> session -> method);
      }

      public Builder method(HttpMethodBuilder method) {
         this.method = method;
         return this;
      }

      /**
       * Issue HTTP GET request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder GET(String path) {
         return method(HttpMethod.GET).path().pattern(path).end();
      }

      /**
       * Issue HTTP GET request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> GET() {
         return method(HttpMethod.GET).path();
      }

      /**
       * Issue HTTP HEAD request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder HEAD(String path) {
         return method(HttpMethod.HEAD).path().pattern(path).end();
      }

      /**
       * Issue HTTP HEAD request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> HEAD() {
         return method(HttpMethod.HEAD).path();
      }

      /**
       * Issue HTTP POST request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder POST(String path) {
         return method(HttpMethod.POST).path().pattern(path).end();
      }

      /**
       * Issue HTTP POST request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> POST() {
         return method(HttpMethod.POST).path();
      }

      /**
       * Issue HTTP PUT request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder PUT(String path) {
         return method(HttpMethod.PUT).path().pattern(path).end();
      }

      /**
       * Issue HTTP PUT request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> PUT() {
         return method(HttpMethod.PUT).path();
      }

      /**
       * Issue HTTP DELETE request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder DELETE(String path) {
         return method(HttpMethod.DELETE).path().pattern(path).end();
      }

      /**
       * Issue HTTP DELETE request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> DELETE() {
         return method(HttpMethod.DELETE).path();
      }

      /**
       * Issue HTTP OPTIONS request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder OPTIONS(String path) {
         return method(HttpMethod.OPTIONS).path().pattern(path).end();
      }

      /**
       * Issue HTTP OPTIONS request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> OPTIONS() {
         return method(HttpMethod.OPTIONS).path();
      }

      /**
       * Issue HTTP PATCH request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder PATCH(String path) {
         return method(HttpMethod.PATCH).path().pattern(path).end();
      }

      /**
       * Issue HTTP PATCH request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> PATCH() {
         return method(HttpMethod.PATCH).path();
      }

      /**
       * Issue HTTP TRACE request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder TRACE(String path) {
         return method(HttpMethod.TRACE).path().pattern(path).end();
      }

      /**
       * Issue HTTP TRACE request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> TRACE() {
         return method(HttpMethod.TRACE).path();
      }

      /**
       * Issue HTTP CONNECT request to given path.
       *
       * @param path HTTP path, a pattern replacing <code>${sessionvar}</code> with variable contents.
       * @return Self.
       */
      public Builder CONNECT(String path) {
         return method(HttpMethod.CONNECT).path().pattern(path).end();
      }

      /**
       * Issue HTTP CONNECT request to given path.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> CONNECT() {
         return method(HttpMethod.CONNECT).path();
      }

      /**
       * HTTP authority (host:port) this request should target. Must match one of the entries in <code>http</code> section.
       *
       * @param authority Host:port.
       * @return Self.
       */
      public Builder authority(String authority) {
         return authority(() -> new Pattern(authority, false));
      }

      public Builder authority(SerializableFunction<Session, String> authorityGenerator) {
         return authority(() -> authorityGenerator);
      }

      /**
       * HTTP authority (host:port) this request should target. Must match one of the entries in <code>http</code> section.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> authority() {
         StringGeneratorImplBuilder<Builder> builder = new StringGeneratorImplBuilder<>(this, false);
         authority(builder);
         return builder;
      }

      public Builder authority(StringGeneratorBuilder authority) {
         this.authority = authority;
         return this;
      }

      StringGeneratorBuilder getAuthority() {
         return authority;
      }

      public Builder path(String path) {
         return path(() -> new Pattern(path, false));
      }

      /**
       * HTTP path (absolute or relative), including query and fragment.
       *
       * @return Builder.
       */
      public StringGeneratorImplBuilder<Builder> path() {
         StringGeneratorImplBuilder<Builder> builder = new StringGeneratorImplBuilder<>(this, false);
         path(builder);
         return builder;
      }

      public Builder path(SerializableFunction<Session, String> pathGenerator) {
         return path(() -> pathGenerator);
      }

      public Builder path(StringGeneratorBuilder builder) {
         if (this.path != null) {
            throw new BenchmarkDefinitionException("Path generator already set.");
         }
         this.path = builder;
         return this;
      }

      /**
       * HTTP request body (possibly a pattern).
       *
       * @param string Request body.
       * @return Self.
       */
      public Builder body(String string) {
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

      public Builder body(SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator) {
         return body(() -> bodyGenerator);
      }

      public Builder body(BodyGeneratorBuilder bodyGenerator) {
         if (this.body != null) {
            throw new BenchmarkDefinitionException("Body generator already set.");
         }
         this.body = bodyGenerator;
         return this;
      }

      BodyGeneratorBuilder bodyBuilder() {
         return body;
      }

      public Builder headerAppender(SerializableBiConsumer<Session, HttpRequestWriter> headerAppender) {
         headerAppenders.add(() -> headerAppender);
         return this;
      }

      public Builder headerAppenders(Collection<? extends Supplier<SerializableBiConsumer<Session, HttpRequestWriter>>> appenders) {
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

      public Builder timeout(long timeout, TimeUnit timeUnit) {
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
      public Builder timeout(String timeout) {
         return timeout(io.hyperfoil.util.Util.parseToMillis(timeout), TimeUnit.MILLISECONDS);
      }

      /**
       * Requests statistics will use this metric name.
       *
       * @param name Metric name.
       * @return Self.
       */
      public Builder metric(String name) {
         return metric((authority, path) -> name);
      }

      public Builder metric(SerializableBiFunction<String, String, String> selector) {
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
      public Builder sync(boolean sync) {
         this.sync = sync;
         return this;
      }

      /**
       * List of SLAs the requests are subject to.
       *
       * @return Builder.
       */
      public SLABuilder.ListBuilder<Builder> sla() {
         if (sla == null) {
            sla = new SLABuilder.ListBuilder<>(this);
         }
         return sla;
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

         ErgonomicsBuilder ergonomics = locator.benchmark().ergonomics();
         if (ergonomics.repeatCookies()) {
            headerAppender(new CookieAppender());
         }
         if (ergonomics.userAgentFromSession()) {
            headerAppender(new UserAgentAppender());
         }
         if (sync) {
            // We need to perform this in prepareBuild() because the completion handlers must not be modified
            // in the build() method. Alternative would be caching the key and returning the wrapping steps
            // in the list.
            BeforeSyncRequestStep beforeSyncRequestStep = new BeforeSyncRequestStep();
            locator.sequence().insertBefore(locator).step(beforeSyncRequestStep);
            handler.onCompletion(s -> s.getResource(beforeSyncRequestStep).set(s.currentSequence().index()));
            locator.sequence().insertAfter(locator).step(new AfterSyncRequestStep(beforeSyncRequestStep));
         }
         handler.prepareBuild();
      }

      @Override
      public List<Step> build() {
         String guessedAuthority = null;
         boolean checkAuthority = true;
         SerializableFunction<Session, String> authority = this.authority != null ? this.authority.build() : null;
         SerializableFunction<Session, String> pathGenerator = this.path != null ? this.path.build() : null;
         try {
            guessedAuthority = authority == null ? null : authority.apply(null);
         } catch (Throwable e) {
            checkAuthority = false;
         }
         if (checkAuthority && !Locator.current().benchmark().validateAuthority(guessedAuthority)) {
            String guessedPath = "<unknown path>";
            try {
               if (pathGenerator != null) {
                  guessedPath = pathGenerator.apply(null);
               }
            } catch (Throwable e) {
            }
            if (authority == null) {
               throw new BenchmarkDefinitionException(String.format("%s to <default route>%s is invalid as we don't have a default route set.", method, guessedPath));
            } else if (!guessedAuthority.contains(":")) {
               throw new BenchmarkDefinitionException(String.format("%s to %s%s is invalid - did you forget the port number?.", method, guessedAuthority, guessedPath));
            } else {
               throw new BenchmarkDefinitionException(String.format("%s to %s%s is invalid - no HTTP configuration defined.", method, guessedAuthority, guessedPath));
            }
         }
         @SuppressWarnings("unchecked")
         SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders =
               this.headerAppenders.isEmpty() ? null :
                     this.headerAppenders.stream().map(Supplier::get).toArray(SerializableBiConsumer[]::new);

         SLA[] sla = this.sla != null ? this.sla.build() : SLA.DEFAULT;
         SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator = this.body != null ? this.body.build() : null;

         SerializableBiFunction<String, String, String> metricSelector = this.metricSelector;
         if (metricSelector == null) {
            String sequenceName = Locator.current().sequence().name();
            metricSelector = (a, p) -> sequenceName;
         }
         HttpRequestStep step = new HttpRequestStep(stepId, method.build(), authority, pathGenerator, bodyGenerator, headerAppenders, injectHostHeader, metricSelector, timeout, handler.build(), sla);
         return Collections.singletonList(step);
      }
   }

   private static class BeforeSyncRequestStep implements Step, ResourceUtilizer, Session.ResourceKey<BitSetResource> {
      @Override
      public boolean invoke(Session s) {
         BitSetResource resource = s.getResource(this);
         resource.clear(s.currentSequence().index());
         return true;
      }

      @Override
      public void reserve(Session session) {
         int concurrency = session.currentSequence().definition().concurrency();
         session.declareResource(this, () -> new BitSetResource(concurrency), true);
      }
   }

   private static class AfterSyncRequestStep implements Step {
      private final Session.ResourceKey<BitSetResource> key;

      private AfterSyncRequestStep(Session.ResourceKey<BitSetResource> key) {
         this.key = key;
      }

      @Override
      public boolean invoke(Session session) {
         BitSetResource resource = session.getResource(key);
         return resource.get(session.currentSequence().index());
      }
   }

   public static class HeadersBuilder extends PairBuilder.OfString implements PartialBuilder {
      private final Builder parent;

      public HeadersBuilder(Builder builder) {
         this.parent = builder;
      }

      public HeadersBuilder header(CharSequence header, CharSequence value) {
         warnIfUsingHostHeader(header);
         parent.headerAppender((session, writer) -> writer.putHeader(header, value));
         return this;
      }

      /**
       * Use header name (e.g. <code>Content-Type</code>) as key and value (possibly a pattern).
       */
      @Override
      public void accept(String header, String value) {
         withKey(header).pattern(value);
      }

      public Builder endHeaders() {
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
         parent.parent.headerAppenders.add(() -> {
            Access access = SessionFactory.access(var);
            String myHeader = header;
            return (session, writer) -> {
               Object value = access.getObject(session);
               if (value instanceof CharSequence) {
                  writer.putHeader(myHeader, (CharSequence) value);
               } else {
                  log.error("#{} Cannot convert variable {}: {} to CharSequence", session.uniqueId(), access, value);
               }
            };
         });
         return this;
      }

      /**
       * Load header value using a pattern.
       *
       * @param patternString Pattern to be encoded, e.g. <code>foo${variable}bar${another-variable}</code>
       * @return Builder.
       */
      public PartialHeadersBuilder pattern(String patternString) {
         ensureOnce();
         parent.parent.headerAppenders.add(() -> {
            Pattern pattern = new Pattern(patternString, false);
            String myHeader = header;
            return (session, writer) -> {
               String value = pattern.apply(session);
               writer.putHeader(myHeader, value);
            };
         });
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
   }

   public interface BodyGeneratorBuilder extends BuilderBase<BodyGeneratorBuilder> {
      SerializableBiFunction<Session, Connection, ByteBuf> build();
   }

   /**
    * Allows building HTTP request body from session variables.
    */
   public static class BodyBuilder {
      private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
      private final Builder parent;

      private BodyBuilder(Builder parent) {
         this.parent = parent;
      }

      /**
       * Use variable content as request body.
       *
       * @param var Variable name.
       * @return Self.
       */
      public BodyBuilder fromVar(String var) {
         parent.body(() -> {
            Access access = SessionFactory.access(var);
            return (session, connection) -> {
               Object value = access.getObject(session);
               if (value instanceof ByteBuf) {
                  return (ByteBuf) value;
               } else if (value instanceof String) {
                  String str = (String) value;
                  return Util.string2byteBuf(str, connection.context().alloc().buffer(str.length()));
               } else if (value instanceof byte[]) {
                  return Unpooled.wrappedBuffer((byte[]) value);
               } else {
                  log.error("#{} Cannot encode request body from var {}: {}", session.uniqueId(), access, value);
                  return null;
               }
            };
         });
         return this;
      }

      /**
       * Pattern replacing <code>${sessionvar}</code> with variable contents in a string.
       *
       * @param pattern Pattern.
       * @return Self.
       */
      public BodyBuilder pattern(String pattern) {
         parent.body(() -> {
            Pattern p = new Pattern(pattern, false);
            return (session, connection) -> {
               String str = p.apply(session);
               return Util.string2byteBuf(str, connection.context().alloc().buffer(str.length()));
            };
         });
         return this;
      }

      /**
       * String sent as-is.
       *
       * @param text String.
       * @return Self.
       */
      public BodyBuilder text(String text) {
         parent.body(new ConstantBytesGenerator(text.getBytes(StandardCharsets.UTF_8)));
         return this;
      }

      /**
       * Build form as if we were sending the request using HTML form. This option automatically adds
       * <code>Content-Type: application/x-www-form-urlencoded</code> to the request headers.
       *
       * @return Builder.
       */
      public FormBuilder form() {
         FormBuilder builder = new FormBuilder();
         parent.headerAppender((session, writer) -> writer.putHeader(HttpHeaderNames.CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED));
         parent.body(builder);
         return builder;
      }

      /**
       * Send contents of the file. Note that this method does NOT set content-type automatically.
       *
       * @param path Path to loaded file.
       * @return Self.
       */
      public BodyBuilder fromFile(String path) {
         parent.body(() -> {
            try (InputStream inputStream = Locator.current().benchmark().data().readFile(path)) {
               if (inputStream == null) {
                  throw new BenchmarkDefinitionException("Cannot load file `" + path + "` for randomItem (not found).");
               }
               byte[] bytes = io.hyperfoil.util.Util.toByteArray(inputStream);
               return new ConstantBytesGenerator(bytes);
            } catch (IOException e) {
               throw new BenchmarkDefinitionException("Cannot load file `" + path + "` for randomItem.", e);
            }
         });
         return this;
      }

      public Builder endBody() {
         return parent;
      }
   }

   /**
    * Build an URL-encoded HTML form body.
    */
   // Note: we cannot implement both a PairBuilder and MappingListBuilder at the same time
   public static class FormBuilder implements BodyGeneratorBuilder, MappingListBuilder<FormInputBuilder> {
      private final ArrayList<FormInputBuilder> inputs = new ArrayList<>();

      /**
       * Add input pair described in the mapping.
       *
       * @return Builder.
       */
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

   /**
    * Form element (e.g. as if coming from an INPUT field).
    */
   public static class FormInputBuilder {
      private String name;
      private String value;
      private String fromVar;
      private String pattern;

      public SerializableBiConsumer<Session, ByteBuf> build() {
         if (value != null && fromVar != null && pattern != null) {
            throw new BenchmarkDefinitionException("Form input: Must set only one of 'value', 'var', 'pattern'");
         } else if (value == null && fromVar == null && pattern == null) {
            throw new BenchmarkDefinitionException("Form input: Must set one of 'value' or 'var' or 'pattern'");
         } else if (name == null) {
            throw new BenchmarkDefinitionException("Form input: 'name' must be set.");
         }
         try {
            byte[] nameBytes = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
            if (value != null) {
               byte[] valueBytes = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
               return (session, buf) -> buf.writeBytes(nameBytes).writeByte('=').writeBytes(valueBytes);
            } else if (fromVar != null) {
               String myVar = this.fromVar; // avoid this capture
               Access access = SessionFactory.access(fromVar);
               return (session, buf) -> {
                  buf.writeBytes(nameBytes).writeByte('=');
                  Session.Var var = access.getVar(session);
                  if (!var.isSet()) {
                     throw new IllegalStateException("Variable " + myVar + " was not set yet!");
                  }
                  if (var.type() == VarType.INTEGER) {
                     Util.intAsText2byteBuf(var.intValue(session), buf);
                  } else if (var.type() == VarType.OBJECT) {
                     Object o = var.objectValue(session);
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
               Pattern pattern = new Pattern(this.pattern, true);
               return (session, buf) -> {
                  buf.writeBytes(nameBytes).writeByte('=');
                  pattern.accept(session, buf);
               };
            }
         } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
         }
      }

      /**
       * Input field name.
       *
       * @param name Input name.
       * @return Self.
       */
      public FormInputBuilder name(String name) {
         this.name = name;
         return this;
      }

      /**
       * Input field value (verbatim).
       *
       * @param value Input value.
       * @return Self.
       */
      public FormInputBuilder value(String value) {
         this.value = value;
         return this;
      }

      /**
       * Input field value from session variable.
       *
       * @param var Variable name.
       * @return Self.
       */
      public FormInputBuilder fromVar(String var) {
         this.fromVar = var;
         return this;
      }

      /**
       * Input field value replacing session variables in a pattern, e.g. <code>foo${myvariable}var</code>
       *
       * @param pattern Template pattern.
       * @return Self.
       */
      public FormInputBuilder pattern(String pattern) {
         this.pattern = pattern;
         return this;
      }
   }
}
