package io.hyperfoil.core.steps;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Rewritable;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.FollowRedirect;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.handlers.ConditionalAction;
import io.hyperfoil.core.handlers.http.ConditionalHeaderHandler;
import io.hyperfoil.core.handlers.ConditionalProcessor;
import io.hyperfoil.core.handlers.http.RangeStatusValidator;
import io.hyperfoil.core.handlers.http.Redirect;
import io.hyperfoil.core.http.CookieRecorder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Unique;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, ResourceUtilizer, Serializable {
   private static final Logger log = LoggerFactory.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   final io.hyperfoil.api.http.StatusHandler[] statusHandlers;
   final HeaderHandler[] headerHandlers;
   final Processor[] bodyHandlers;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(io.hyperfoil.api.http.StatusHandler[] statusHandlers,
                                    HeaderHandler[] headerHandlers,
                                    Processor[] bodyHandlers,
                                    Action[] completionHandlers,
                                    RawBytesHandler[] rawBytesHandlers) {
      this.statusHandlers = statusHandlers;
      this.headerHandlers = headerHandlers;
      this.bodyHandlers = bodyHandlers;
      this.completionHandlers = completionHandlers;
      this.rawBytesHandlers = rawBytesHandlers;
   }

   @Override
   public void handleStatus(HttpRequest request, int status, String reason) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring status {} as the request has been marked completed (failed).", session.uniqueId(), status);
         }
         return;
      }

      if (trace) {
         log.trace("#{} Received status {}: {}", session.uniqueId(), status, reason);
      }

      switch (request.method) {
         case GET:
         case HEAD:
            // TODO: should we store 203, 300, 301 and 410?
            // TODO: partial response 206
            if (status != 200) {
               request.cacheControl.noStore = true;
            }
            break;
         case POST:
         case PUT:
         case DELETE:
         case PATCH:
            if (status >= 200 && status <= 399) {
               request.session.httpCache().invalidate(request.authority, request.path);
               request.cacheControl.invalidate = true;
            }
            request.cacheControl.noStore = true;
            break;
      }

      request.statistics().addStatus(request.startTimestampMillis(), status);
      if (statusHandlers != null) {
         for (io.hyperfoil.api.http.StatusHandler handler : statusHandlers) {
            handler.handleStatus(request, status);
         }
      }

      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.beforeHeaders(request);
         }
      }
      if (bodyHandlers != null) {
         for (Processor handler : bodyHandlers) {
            handler.before(request.session);
         }
      }
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring header on a failed request: {}: {}", session.uniqueId(), header, value);
         }
         return;
      }
      if (trace) {
         log.trace("#{} Received header {}: {}", session.uniqueId(), header, value);
      }
      if (request.cacheControl.invalidate) {
         if (AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.LOCATION)
               || AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.CONTENT_LOCATION)) {
            session.httpCache().invalidate(request.authority, value);
         }
      }
      if (headerHandlers != null) {
         for (HeaderHandler handler : headerHandlers) {
            handler.handleHeader(request, header, value);
         }
      }
      request.session.httpCache().responseHeader(request, header, value);
   }

   @Override
   public void handleThrowable(HttpRequest request, Throwable throwable) {
      Session session = request.session;
      if (trace) {
         log.trace("#{} {} Received exception", throwable, session.uniqueId(), request);
      }
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Request has been already completed", session.uniqueId());
         }
         return;
      }
      if (request.isValid()) {
         // Do not mark as invalid when timed out
         request.markInvalid();
      }
      session.currentRequest(request);

      try {
         if (request.isRunning()) {
            request.setCompleting();
            if (completionHandlers != null) {
               for (Action handler : completionHandlers) {
                  handler.run(session);
               }
            }
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.warn("#{} Exception {} thrown while handling another exception: ", throwable, session.uniqueId(), t.toString());
         t.addSuppressed(throwable);
         throw t;
      } finally {
         request.statistics().incrementResets(request.startTimestampMillis());
         request.setCompleted();
      }
   }

   @Override
   public void handleBodyPart(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Ignoring body part ({} bytes) on a failed request.", session.uniqueId(), data.readableBytes());
         }
         return;
      }
      session.currentRequest(request);

      if (trace) {
         log.trace("#{} Received part ({} bytes):\n{}", session.uniqueId(), length,
               data.toString(offset, length, StandardCharsets.UTF_8));
      }

      int dataStartIndex = data.readerIndex();
      if (bodyHandlers != null) {
         for (Processor handler : bodyHandlers) {
            handler.process(request.session, data, offset, length, isLastPart);
            data.readerIndex(dataStartIndex);
         }
      }
      session.currentRequest(null);
   }

   @Override
   public void handleEnd(HttpRequest request, boolean executed) {
      Session session = request.session;
      if (request.isCompleted()) {
         if (trace) {
            log.trace("#{} Request has been already completed.", session.uniqueId());
         }
         return;
      }
      session.currentRequest(request);
      if (trace) {
         log.trace("#{} Completed request on {}", session.uniqueId(), request.connection());
      }

      try {
         if (executed) {
            long endTime = System.nanoTime();
            request.statistics().recordResponse(request.startTimestampMillis(), request.sendTimestampNanos() - request.startTimestampNanos(), endTime - request.startTimestampNanos());

            if (headerHandlers != null) {
               for (HeaderHandler handler : headerHandlers) {
                  handler.afterHeaders(request);
               }
            }
            if (bodyHandlers != null) {
               for (Processor handler : bodyHandlers) {
                  handler.after(request.session);
               }
            }
            request.session.httpCache().tryStore(request);
         }

         if (request.isRunning()) {
            request.setCompleting();
            if (completionHandlers != null) {
               for (Action handler : completionHandlers) {
                  handler.run(session);
               }
            }
         }
      } finally {
         if (executed && !request.isValid()) {
            request.statistics().addInvalid(request.startTimestampMillis());
         }
         request.setCompleted();
      }
   }

   @Override
   public void handleRawRequest(HttpRequest request, ByteBuf data, int offset, int length) {
      if (rawBytesHandlers == null) {
         return;
      }
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.onRequest(request, data, offset, length);
      }
   }

   @Override
   public void handleRawResponse(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (rawBytesHandlers == null) {
         return;
      }
      for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
         rawBytesHandler.onResponse(request, data, offset, length, isLastPart);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) statusHandlers);
      ResourceUtilizer.reserve(session, (Object[]) headerHandlers);
      ResourceUtilizer.reserve(session, (Object[]) bodyHandlers);
      ResourceUtilizer.reserve(session, (Object[]) completionHandlers);
      ResourceUtilizer.reserve(session, (Object[]) rawBytesHandlers);
   }

   /**
    * Manages processing of HTTP responses.
    */
   public static class Builder implements Rewritable<Builder> {
      // prevents some weird serialization incompatibility
      private static final Action STOP_ON_INVALID_RESPONSE = session -> {
         if (!session.currentRequest().isValid()) {
            session.stop();
         }
      };
      private final HttpRequestStep.Builder parent;
      private Boolean autoRangeCheck;
      private Boolean stopOnInvalid;
      private FollowRedirect followRedirect;
      private List<io.hyperfoil.api.http.StatusHandler.Builder> statusHandlers = new ArrayList<>();
      private List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
      private List<HttpRequestProcessorBuilder> bodyHandlers = new ArrayList<>();
      private List<Action.Builder> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler.Builder> rawBytesHandlers = new ArrayList<>();

      public static Builder forTesting() {
         return new Builder(null);
      }

      Builder(HttpRequestStep.Builder parent) {
         this.parent = parent;
      }

      public Builder status(io.hyperfoil.api.http.StatusHandler.Builder builder) {
         statusHandlers.add(builder);
         return this;
      }

      public Builder status(io.hyperfoil.api.http.StatusHandler handler) {
         statusHandlers.add(() -> handler);
         return this;
      }

      /**
       * Handle HTTP response status.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<io.hyperfoil.api.http.StatusHandler.Builder> status() {
         return new ServiceLoadedBuilderProvider<>(io.hyperfoil.api.http.StatusHandler.Builder.class, statusHandlers::add);
      }

      public Builder header(HeaderHandler handler) {
         return header(() -> handler);
      }

      public Builder header(HeaderHandler.Builder builder) {
         headerHandlers.add(builder);
         return this;
      }

      /**
       * Handle HTTP response headers.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HeaderHandler.Builder> header() {
         return new ServiceLoadedBuilderProvider<>(HeaderHandler.Builder.class, headerHandlers::add);
      }

      public Builder body(HttpRequestProcessorBuilder builder) {
         bodyHandlers.add(builder);
         return this;
      }

      /**
       * Handle HTTP response body.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<HttpRequestProcessorBuilder> body() {
         return new ServiceLoadedBuilderProvider<>(HttpRequestProcessorBuilder.class, bodyHandlers::add);
      }

      public Builder onCompletion(Action handler) {
         return onCompletion(() -> handler);
      }

      public Builder onCompletion(Action.Builder builder) {
         completionHandlers.add(builder);
         return this;
      }

      /**
       * Action executed when the HTTP response is fully received.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onCompletion() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, completionHandlers::add);
      }

      public Builder rawBytes(RawBytesHandler handler) {
         rawBytesHandlers.add(() -> handler);
         return this;
      }

      public Builder rawBytes(RawBytesHandler.Builder builder) {
         rawBytesHandlers.add(builder);
         return this;
      }

      /**
       * Handler processing HTTP response before parsing.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<RawBytesHandler.Builder> rawBytes() {
         return new ServiceLoadedBuilderProvider<>(RawBytesHandler.Builder.class, this::rawBytes);
      }

      public Builder autoRangeCheck(boolean autoRangeCheck) {
         this.autoRangeCheck = autoRangeCheck;
         return this;
      }

      public Builder stopOnInvalid(boolean stopOnInvalid) {
         this.stopOnInvalid = stopOnInvalid;
         return this;
      }

      public Builder followRedirect(FollowRedirect followRedirect) {
         this.followRedirect = followRedirect;
         if (followRedirect == FollowRedirect.ALWAYS || followRedirect == FollowRedirect.HTML_ONLY) {
            throw new IllegalArgumentException("FollowRedirect with HTML not implemented.");
         }
         return this;
      }

      public HttpRequestStep.Builder endHandler() {
         return parent;
      }

      public void prepareBuild() {
         ErgonomicsBuilder ergonomics = Locator.current().benchmark().ergonomics();
         if (ergonomics.repeatCookies()) {
            header(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusHandlers.forEach(io.hyperfoil.api.http.StatusHandler.Builder::prepareBuild);
         headerHandlers.forEach(HeaderHandler.Builder::prepareBuild);
         bodyHandlers.forEach(HttpRequestProcessorBuilder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
         rawBytesHandlers.forEach(RawBytesHandler.Builder::prepareBuild);
         if (autoRangeCheck != null ? autoRangeCheck : ergonomics.autoRangeCheck()) {
            statusHandlers.add(new RangeStatusValidator.Builder().min(200).max(399));
         }
         // We must add this as the very last action since after calling session.stop() there other handlers won't be called
         if (stopOnInvalid != null ? stopOnInvalid : ergonomics.stopOnInvalid()) {
            completionHandlers.add(() -> STOP_ON_INVALID_RESPONSE);
         }
         FollowRedirect followRedirect = this.followRedirect != null ? this.followRedirect : ergonomics.followRedirect();
         switch (followRedirect) {
            case LOCATION_ONLY:
            case ALWAYS:
               applyLocationRedirect();
               break;
            case HTML_ONLY:
               throw new UnsupportedOperationException("HTML redirect not implemented");
         }
      }

      protected void applyLocationRedirect() {
         Locator locator = Locator.current();
         // Not sequence-scoped as queue output var, requesting sequence-scoped access explicitly where needed
         Unique coordVar = new Unique();
         String redirectSequenceName = String.format("%s_redirect_%08x",
               locator.sequence().name(), ThreadLocalRandom.current().nextInt());
         // The redirect sequence (and queue) needs to have twice the concurrency of the original sequence
         // because while executing one redirect it might activate a second redirect
         int redirectConcurrency = 2 * Math.max(1, locator.sequence().rootSequence().concurrency());

         LimitedPoolResource.Key<Redirect.Coords> poolKey = new LimitedPoolResource.Key<>();
         Session.ResourceKey<Queue> queueKey = new Queue.Key();

         {
            // Note: there's a lot of copy-paste between current handler because we need to use
            // different method variable for current sequence and new sequence since these have incompatible
            // indices - had we used the same var one sequence would overwrite other's var.
            Unique newMethodVar = new Unique(true);
            HttpRequestStep.BodyGeneratorBuilder bodyBuilder = parent.bodyBuilder();
            HttpRequestStep.Builder httpRequest = new HttpRequestStep.Builder()
                  .method(() -> new Redirect.GetMethod(SessionFactory.sequenceScopedAccess(coordVar)))
                  .path(() -> new Redirect.GetLocation(SessionFactory.sequenceScopedAccess(coordVar)))
                  .authority(parent.getAuthority())
                  .headerAppenders(parent.headerAppenders())
                  .body(bodyBuilder == null ? null : bodyBuilder.copy())
                  .sync(false);
            httpRequest.handler()
                  // we want to reuse the same sequence
                  // TODO: something with HTML redirect
                  .followRedirect(FollowRedirect.NEVER)
                  // support recursion
                  .status(new Redirect.StatusHandler.Builder().methodVar(newMethodVar).handlers(statusHandlers))
                  .header(new Redirect.LocationRecorder.Builder()
                        .originalSequenceSupplier(() -> {
                           Access access = SessionFactory.sequenceScopedAccess(coordVar);
                           return s -> ((Redirect.Coords) access.getObject(s)).originalSequence;
                        })
                        .concurrency(redirectConcurrency).inputVar(newMethodVar).outputVar(coordVar).queueKey(queueKey).poolKey(poolKey).sequence(redirectSequenceName));
            if (!headerHandlers.isEmpty()) {
               httpRequest.handler().header(new ConditionalHeaderHandler.Builder()
                     .condition().stringCondition().fromVar(newMethodVar).isSet(false).end()
                     .handler(new Redirect.WrappingHeaderHandler.Builder().coordVar(coordVar).handlers(headerHandlers)));
            }
            if (!bodyHandlers.isEmpty()) {
               httpRequest.handler().body(HttpRequestProcessorBuilder.adapt(new ConditionalProcessor.Builder()
                     .condition().stringCondition().fromVar(newMethodVar).isSet(false).end()
                     .processor(new Redirect.WrappingProcessor.Builder().coordVar(coordVar).processors(bodyHandlers))));
            }
            if (!completionHandlers.isEmpty()) {
               httpRequest.handler().onCompletion(new ConditionalAction.Builder()
                     .condition().stringCondition().fromVar(newMethodVar).isSet(false).end()
                     .action(new Redirect.WrappingAction.Builder().coordVar(coordVar).actions(completionHandlers)));
            }
            httpRequest.handler().onCompletion(() -> new Redirect.Complete(poolKey, queueKey, SessionFactory.sequenceScopedAccess(coordVar)));
            SequenceBuilder redirectSequence = locator.scenario().sequence(redirectSequenceName)
                  .concurrency(redirectConcurrency)
                  .stepBuilder(httpRequest)
                  .rootSequence();
            redirectSequence.prepareBuild();
         }

         Unique methodVar = new Unique(locator.sequence().rootSequence().concurrency() > 0);
         statusHandlers = Collections.singletonList(
               new Redirect.StatusHandler.Builder().methodVar(methodVar).handlers(statusHandlers));

         // Note: we are using stringCondition.isSet despite the variable is not a string - it doesn't matter for the purpose of this check
         List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
         headerHandlers.add(new Redirect.LocationRecorder.Builder()
               .originalSequenceSupplier(() -> Session::currentSequence)
               .concurrency(redirectConcurrency).inputVar(methodVar).outputVar(coordVar).queueKey(queueKey).poolKey(poolKey).sequence(redirectSequenceName));
         if (!this.headerHandlers.isEmpty()) {
            headerHandlers.add(new ConditionalHeaderHandler.Builder()
                  .condition().stringCondition().fromVar(methodVar).isSet(false).end()
                  .handlers(this.headerHandlers));
         }
         this.headerHandlers = headerHandlers;

         if (!bodyHandlers.isEmpty()) {
            bodyHandlers = Collections.singletonList(
                  HttpRequestProcessorBuilder.adapt(new ConditionalProcessor.Builder()
                        .condition().stringCondition().fromVar(methodVar).isSet(false).end()
                        .processors(bodyHandlers)));
         }
         if (!completionHandlers.isEmpty()) {
            completionHandlers = Collections.singletonList(
                  new ConditionalAction.Builder()
                        .condition().stringCondition().fromVar(methodVar).isSet(false).end()
                        .actions(completionHandlers));
         }
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusHandlers, io.hyperfoil.api.http.StatusHandler.Builder::build, io.hyperfoil.api.http.StatusHandler[]::new),
               toArray(headerHandlers, HeaderHandler.Builder::build, HeaderHandler[]::new),
               toArray(bodyHandlers, b -> b.build(true), Processor[]::new),
               toArray(completionHandlers, Action.Builder::build, Action[]::new),
               toArray(rawBytesHandlers, RawBytesHandler.Builder::build, RawBytesHandler[]::new));
      }

      private static <B, T> T[] toArray(List<B> list, Function<B, T> build, IntFunction<T[]> generator) {
         if (list.isEmpty()) {
            return null;
         } else {
            return list.stream().map(build).toArray(generator);
         }
      }

      @Override
      public void readFrom(Builder other) {
         statusHandlers.addAll(BuilderBase.copy(other.statusHandlers));
         headerHandlers.addAll(BuilderBase.copy(other.headerHandlers));
         bodyHandlers.addAll(BuilderBase.copy(other.bodyHandlers));
         completionHandlers.addAll(BuilderBase.copy(other.completionHandlers));
         rawBytesHandlers.addAll(BuilderBase.copy(other.rawBytesHandlers));
         autoRangeCheck = other.autoRangeCheck;
         stopOnInvalid = other.stopOnInvalid;
         followRedirect = other.followRedirect;
      }

   }
}
