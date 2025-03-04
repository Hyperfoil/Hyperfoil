package io.hyperfoil.http.steps;

import static io.hyperfoil.core.session.SessionFactory.objectAccess;
import static io.hyperfoil.core.session.SessionFactory.sequenceScopedObjectAccess;
import static io.hyperfoil.core.session.SessionFactory.sequenceScopedReadAccess;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.handlers.ConditionalAction;
import io.hyperfoil.core.handlers.ConditionalProcessor;
import io.hyperfoil.core.steps.AwaitDelayStep;
import io.hyperfoil.core.steps.PushQueueAction;
import io.hyperfoil.core.steps.ScheduleDelayStep;
import io.hyperfoil.core.util.Unique;
import io.hyperfoil.function.SerializableToLongFunction;
import io.hyperfoil.http.api.FollowRedirect;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.http.config.HttpErgonomics;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.cookie.CookieRecorder;
import io.hyperfoil.http.handlers.ConditionalHeaderHandler;
import io.hyperfoil.http.handlers.Location;
import io.hyperfoil.http.handlers.RangeStatusValidator;
import io.hyperfoil.http.handlers.Redirect;
import io.hyperfoil.http.html.HtmlHandler;
import io.hyperfoil.http.html.MetaRefreshHandler;
import io.hyperfoil.http.html.RefreshHandler;
import io.hyperfoil.http.statistics.HttpStats;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

public class HttpResponseHandlersImpl implements HttpResponseHandlers, Serializable {
   private static final Logger log = LogManager.getLogger(HttpResponseHandlersImpl.class);
   private static final boolean trace = log.isTraceEnabled();

   final StatusHandler[] statusHandlers;
   final HeaderHandler[] headerHandlers;
   final Processor[] bodyHandlers;
   final Action[] completionHandlers;
   final RawBytesHandler[] rawBytesHandlers;

   private HttpResponseHandlersImpl(StatusHandler[] statusHandlers,
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

      try {
         switch (request.method) {
            case GET:
            case HEAD:
               // TODO: should we store 203, 300, 301 and 410?
               // TODO: partial response 206
               if (status != 200 && request.hasCacheControl()) {
                  request.cacheControl.noStore = true;
               }
               break;
            case POST:
            case PUT:
            case DELETE:
            case PATCH:
               if (request.hasCacheControl()) {
                  if (status >= 200 && status <= 399) {
                     HttpCache.get(request.session).invalidate(request.authority, request.path);
                     request.cacheControl.invalidate = true;
                  }
                  request.cacheControl.noStore = true;
               }
               break;
         }

         HttpStats.addStatus(request.statistics(), request.startTimestampMillis(), status);
         if (statusHandlers != null) {
            for (StatusHandler handler : statusHandlers) {
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
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Response status processing failed on {}", session.uniqueId(), this), t);
         request.statistics().incrementInternalErrors(request.startTimestampMillis());
         request.markInvalid();
         session.stop();
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
      try {
         HttpCache httpCache = request.hasCacheControl() ? HttpCache.get(session) : null;
         if (httpCache != null && request.cacheControl.invalidate) {
            if (AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.LOCATION)
                  || AsciiString.contentEqualsIgnoreCase(header, HttpHeaderNames.CONTENT_LOCATION)) {
               httpCache.invalidate(request.authority, value);
            }
         }
         if (headerHandlers != null) {
            for (HeaderHandler handler : headerHandlers) {
               handler.handleHeader(request, header, value);
            }
         }
         if (httpCache != null) {
            httpCache.responseHeader(request, header, value);
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Response header processing failed on {}", session.uniqueId(), this), t);
         request.statistics().incrementInternalErrors(request.startTimestampMillis());
         request.markInvalid();
         session.stop();
      }
   }

   @Override
   public void handleThrowable(HttpRequest request, Throwable throwable) {
      Session session = request.session;
      if (log.isDebugEnabled()) {
         log.debug(new FormattedMessage("#{} {} Received exception", session.uniqueId(), request), throwable);
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

      try {
         if (request.isRunning()) {
            request.statistics().incrementConnectionErrors(request.startTimestampMillis());
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
         t.addSuppressed(throwable);
         log.error(new FormattedMessage("#{} Exception {} thrown while handling another exception: ", session.uniqueId(),
               throwable.toString()), t);
         request.statistics().incrementInternalErrors(request.startTimestampMillis());
         session.stop();
      } finally {
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

      if (trace) {
         log.trace("#{} Received part ({} bytes):\n{}", session.uniqueId(), length,
               data.toString(offset, length, StandardCharsets.UTF_8));
      }

      try {
         int dataStartIndex = data.readerIndex();
         if (bodyHandlers != null) {
            for (Processor handler : bodyHandlers) {
               handler.process(request.session, data, offset, length, isLastPart);
               data.readerIndex(dataStartIndex);
            }
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Response body processing failed on {}", session.uniqueId(), this), t);
         request.statistics().incrementInternalErrors(request.startTimestampMillis());
         request.markInvalid();
         session.stop();
      }
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
      if (trace) {
         log.trace("#{} Completed request on {}", session.uniqueId(), request.connection());
      }

      try {
         if (request.isRunning()) {
            request.setCompleting();

            if (executed) {
               request.recordResponse(System.nanoTime());

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
               if (request.hasCacheControl()) {
                  HttpCache.get(request.session).tryStore(request);
               }
            }

            if (completionHandlers != null) {
               for (Action handler : completionHandlers) {
                  handler.run(session);
               }
            }
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Response completion failed on {}, stopping the session.",
               request.session.uniqueId(), this), t);
         request.statistics().incrementInternalErrors(request.startTimestampMillis());
         request.markInvalid();
         session.stop();
      } finally {
         // When one of the handlers calls Session.stop() it may terminate the phase completely,
         // sending stats before this finally-block may run. In that case this request gets completed
         // when cancelling all the request (including the current request) and we record the invalid
         // request directly there.
         if (executed && !request.isValid() && !request.isCompleted()) {
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
      try {
         for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
            rawBytesHandler.onRequest(request, data, offset, length);
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Raw request processing failed on {}", request.session.uniqueId(), this), t);
         request.markInvalid();
         request.session.stop();
      }
   }

   @Override
   public void handleRawResponse(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (rawBytesHandlers == null) {
         return;
      }
      try {
         for (RawBytesHandler rawBytesHandler : rawBytesHandlers) {
            rawBytesHandler.onResponse(request, data, offset, length, isLastPart);
         }
      } catch (SessionStopException e) {
         throw e;
      } catch (Throwable t) {
         log.error(new FormattedMessage("#{} Raw response processing failed on {}", request.session.uniqueId(), this), t);
         request.markInvalid();
         request.session.stop();
      }
   }

   /**
    * Manages processing of HTTP responses.
    */
   public static class Builder implements BuilderBase<Builder> {
      private final HttpRequestStepBuilder parent;
      private Boolean autoRangeCheck;
      private Boolean stopOnInvalid;
      private FollowRedirect followRedirect;
      private List<StatusHandler.Builder> statusHandlers = new ArrayList<>();
      private List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
      private List<Processor.Builder> bodyHandlers = new ArrayList<>();
      private List<Action.Builder> completionHandlers = new ArrayList<>();
      private List<RawBytesHandler.Builder> rawBytesHandlers = new ArrayList<>();

      public static Builder forTesting() {
         return new Builder(null);
      }

      public Builder(HttpRequestStepBuilder parent) {
         this.parent = parent;
      }

      public Builder status(StatusHandler.Builder builder) {
         statusHandlers.add(builder);
         return this;
      }

      public Builder status(StatusHandler handler) {
         statusHandlers.add(() -> handler);
         return this;
      }

      /**
       * Handle HTTP response status.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<StatusHandler.Builder> status() {
         return new ServiceLoadedBuilderProvider<>(StatusHandler.Builder.class, statusHandlers::add);
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

      public Builder body(Processor.Builder builder) {
         bodyHandlers.add(builder);
         return this;
      }

      /**
       * Handle HTTP response body.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Processor.Builder> body() {
         return new ServiceLoadedBuilderProvider<>(Processor.Builder.class, bodyHandlers::add);
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

      /**
       * Inject status handler that marks the request as invalid on status 4xx or 5xx.
       * Default value depends on <code>ergonomics.autoRangeCheck</code>
       * (see <a href="https://hyperfoil.io/docs/user-guide/benchmark/ergonomics">User Guide</a>).
       *
       * @param autoRangeCheck True for inserting the handler, false otherwise.
       * @return Self.
       */
      public Builder autoRangeCheck(boolean autoRangeCheck) {
         this.autoRangeCheck = autoRangeCheck;
         return this;
      }

      /**
       * Inject completion handler that will stop the session if the request has been marked as invalid.
       * Default value depends on <code>ergonomics.stopOnInvalid</code>
       * (see <a href="https://hyperfoil.io/userguide/benchmark/ergonomics.html">User Guide</a>).
       *
       * @param stopOnInvalid Do inject the handler.
       * @return Self.
       */
      public Builder stopOnInvalid(boolean stopOnInvalid) {
         this.stopOnInvalid = stopOnInvalid;
         return this;
      }

      /**
       * Automatically fire requests when the server responds with redirection.
       * Default value depends on <code>ergonomics.followRedirect</code>
       * (see <a href="https://hyperfoil.io/userguide/benchmark/ergonomics.html">User Guide</a>).
       *
       * @param followRedirect Types of server response that will trigger the request.
       * @return Self.
       */
      public Builder followRedirect(FollowRedirect followRedirect) {
         this.followRedirect = followRedirect;
         return this;
      }

      public HttpRequestStepBuilder endHandler() {
         return parent;
      }

      public Builder wrapBodyHandlers(Function<Collection<Processor.Builder>, Processor.Builder> func) {
         Processor.Builder wrapped = func.apply(bodyHandlers);
         this.bodyHandlers = new ArrayList<>();
         this.bodyHandlers.add(wrapped);
         return this;
      }

      public void prepareBuild() {
         HttpErgonomics ergonomics = Locator.current().benchmark().plugin(HttpPluginBuilder.class).ergonomics();
         if (ergonomics.repeatCookies()) {
            header(new CookieRecorder());
         }
         // TODO: we might need defensive copies here
         statusHandlers.forEach(StatusHandler.Builder::prepareBuild);
         headerHandlers.forEach(HeaderHandler.Builder::prepareBuild);
         bodyHandlers.forEach(Processor.Builder::prepareBuild);
         completionHandlers.forEach(Action.Builder::prepareBuild);
         rawBytesHandlers.forEach(RawBytesHandler.Builder::prepareBuild);
         if (autoRangeCheck != null ? autoRangeCheck : ergonomics.autoRangeCheck()) {
            statusHandlers.add(new RangeStatusValidator.Builder().min(200).max(399));
         }
         // We must add this as the very last action since after calling session.stop() there other handlers won't be called
         if (stopOnInvalid != null ? stopOnInvalid : ergonomics.stopOnInvalid()) {
            completionHandlers.add(() -> StopOnInvalidAction.INSTANCE);
         }
         FollowRedirect followRedirect = this.followRedirect != null ? this.followRedirect : ergonomics.followRedirect();
         switch (followRedirect) {
            case LOCATION_ONLY:
               applyRedirect(true, false);
               break;
            case HTML_ONLY:
               applyRedirect(false, true);
               break;
            case ALWAYS:
               applyRedirect(true, true);
               break;
         }
      }

      private void applyRedirect(boolean location, boolean html) {
         Locator locator = Locator.current();
         // Not sequence-scoped as queue output var, requesting sequence-scoped access explicitly where needed
         Unique coordsVar = new Unique();

         String redirectSequenceName = String.format("%s_redirect_%08x",
               locator.sequence().name(), ThreadLocalRandom.current().nextInt());
         String delaySequenceName = String.format("%s_delay_%08x",
               locator.sequence().name(), ThreadLocalRandom.current().nextInt());

         Queue.Key queueKey = new Queue.Key();
         Queue.Key delayedQueueKey = new Queue.Key();
         Unique delayedCoordVar = new Unique();

         int concurrency = Math.max(1, locator.sequence().rootSequence().concurrency());

         if (html) {
            Unique delay = new Unique();
            SequenceBuilder delaySequence = locator.scenario().sequence(delaySequenceName);
            delaySequence.concurrency(Math.max(1, concurrency))
                  .step(() -> {
                     ReadAccess inputVar = sequenceScopedReadAccess(delayedCoordVar);
                     ObjectAccess delayVar = sequenceScopedObjectAccess(delay);
                     SerializableToLongFunction<Session> delayFunc = session -> TimeUnit.SECONDS
                           .toNanos(((Redirect.Coords) inputVar.getObject(session)).delay);
                     return new ScheduleDelayStep(delayVar, ScheduleDelayStep.Type.FROM_NOW, delayFunc);
                  })
                  .step(() -> new AwaitDelayStep(sequenceScopedReadAccess(delay)))
                  .stepBuilder(new StepBuilder.ActionAdapter(() -> new PushQueueAction(
                        sequenceScopedReadAccess(delayedCoordVar), queueKey)))
                  .step(session -> {
                     session.getResource(delayedQueueKey).consumed(session);
                     return true;
                  });
            Locator.push(null, delaySequence);
            delaySequence.prepareBuild();
            Locator.pop();
         }

         // The redirect sequence (and queue) needs to have twice the concurrency of the original sequence
         // because while executing one redirect it might activate a second redirect
         int redirectConcurrency = 2 * concurrency;
         LimitedPoolResource.Key<Redirect.Coords> poolKey = new LimitedPoolResource.Key<>();

         {
            // Note: there's a lot of copy-paste between current handler because we need to use
            // different method variable for current sequence and new sequence since these have incompatible
            // indices - had we used the same var one sequence would overwrite other's var.
            Unique newTempCoordsVar = new Unique(true);
            HttpRequestStepBuilder step = (HttpRequestStepBuilder) locator.step();
            HttpRequestStepBuilder.BodyGeneratorBuilder bodyBuilder = step.bodyBuilder();
            HttpRequestStepBuilder httpRequest = new HttpRequestStepBuilder()
                  .method(() -> new Redirect.GetMethod(sequenceScopedReadAccess(coordsVar)))
                  .path(() -> new Location.GetPath(sequenceScopedReadAccess(coordsVar)))
                  .authority(() -> new Location.GetAuthority(sequenceScopedReadAccess(coordsVar)))
                  .headerAppenders(step.headerAppenders())
                  .body(bodyBuilder == null ? null : bodyBuilder.copy(null))
                  .sync(false)
                  // we want to reuse the same sequence for subsequent requests
                  .handler().followRedirect(FollowRedirect.NEVER).endHandler();
            if (location) {
               httpRequest.handler()
                     .status(new Redirect.StatusHandler.Builder()
                           .poolKey(poolKey).concurrency(redirectConcurrency).coordsVar(newTempCoordsVar)
                           .handlers(statusHandlers))
                     .header(new Redirect.LocationRecorder.Builder()
                           .originalSequenceSupplier(
                                 () -> new Redirect.GetOriginalSequence(sequenceScopedReadAccess(coordsVar)))
                           .concurrency(redirectConcurrency).inputVar(newTempCoordsVar).outputVar(coordsVar)
                           .queueKey(queueKey).sequence(redirectSequenceName));
            }
            if (!headerHandlers.isEmpty()) {
               Redirect.WrappingHeaderHandler.Builder wrappingHandler = new Redirect.WrappingHeaderHandler.Builder()
                     .coordVar(coordsVar).handlers(headerHandlers);
               if (location) {
                  httpRequest.handler().header(new ConditionalHeaderHandler.Builder()
                        .condition().stringCondition().fromVar(newTempCoordsVar).isSet(false).end()
                        .handler(wrappingHandler));
               } else {
                  httpRequest.handler().header(wrappingHandler);
               }
            }
            if (!bodyHandlers.isEmpty() || html) {
               Consumer<Processor.Builder> handlerConsumer;
               ConditionalProcessor.Builder conditionalBodyHandler;
               Redirect.WrappingProcessor.Builder wrappingProcessor = new Redirect.WrappingProcessor.Builder()
                     .coordVar(coordsVar).processors(bodyHandlers);
               if (location) {
                  if (!bodyHandlers.isEmpty() || html) {
                     conditionalBodyHandler = new ConditionalProcessor.Builder()
                           .condition().stringCondition().fromVar(newTempCoordsVar).isSet(false).end()
                           .processor(wrappingProcessor);
                     handlerConsumer = conditionalBodyHandler::processor;
                     httpRequest.handler().body(conditionalBodyHandler);
                  } else {
                     handlerConsumer = null;
                  }
               } else {
                  assert html;
                  httpRequest.handler().body(wrappingProcessor);
                  handlerConsumer = httpRequest.handler()::body;
               }
               if (html) {
                  handlerConsumer.accept(new HtmlHandler.Builder().handler(new MetaRefreshHandler.Builder()
                        .processor(fragmented -> new RefreshHandler(
                              queueKey, delayedQueueKey, poolKey, redirectConcurrency, objectAccess(coordsVar),
                              objectAccess(delayedCoordVar),
                              redirectSequenceName, delaySequenceName, objectAccess(newTempCoordsVar),
                              new Redirect.GetOriginalSequence(sequenceScopedReadAccess(coordsVar))))));
               }
            }
            if (!completionHandlers.isEmpty()) {
               httpRequest.handler().onCompletion(new ConditionalAction.Builder()
                     .condition().stringCondition().fromVar(newTempCoordsVar).isSet(false).end()
                     .action(new Redirect.WrappingAction.Builder().coordVar(coordsVar).actions(completionHandlers)));
            }
            httpRequest.handler()
                  .onCompletion(() -> new Location.Complete<>(poolKey, queueKey, sequenceScopedObjectAccess(coordsVar)));
            SequenceBuilder redirectSequence = locator.scenario().sequence(redirectSequenceName)
                  .concurrency(redirectConcurrency)
                  .stepBuilder(httpRequest)
                  .rootSequence();
            Locator.push(null, redirectSequence);
            redirectSequence.prepareBuild();
            Locator.pop();
         }

         Unique tempCoordsVar = new Unique(locator.sequence().rootSequence().concurrency() > 0);

         if (location) {
            statusHandlers = Collections.singletonList(
                  new Redirect.StatusHandler.Builder().poolKey(poolKey).concurrency(redirectConcurrency)
                        .coordsVar(tempCoordsVar).handlers(statusHandlers));
            List<HeaderHandler.Builder> headerHandlers = new ArrayList<>();
            headerHandlers.add(new Redirect.LocationRecorder.Builder()
                  .originalSequenceSupplier(() -> Session::currentSequence)
                  .concurrency(redirectConcurrency).inputVar(tempCoordsVar).outputVar(coordsVar).queueKey(queueKey)
                  .sequence(redirectSequenceName));
            if (!this.headerHandlers.isEmpty()) {
               // Note: we are using stringCondition.isSet despite the variable is not a string - it doesn't matter for the purpose of this check
               headerHandlers.add(new ConditionalHeaderHandler.Builder()
                     .condition().stringCondition().fromVar(tempCoordsVar).isSet(false).end()
                     .handlers(this.headerHandlers));
            }
            this.headerHandlers = headerHandlers;
         }

         if (!bodyHandlers.isEmpty() || html) {
            Consumer<Processor.Builder> handlerConsumer;
            ConditionalProcessor.Builder conditionalBodyHandler = null;
            if (location) {
               conditionalBodyHandler = new ConditionalProcessor.Builder()
                     .condition().stringCondition().fromVar(tempCoordsVar).isSet(false).end()
                     .processors(bodyHandlers);
               handlerConsumer = conditionalBodyHandler::processor;
            } else {
               handlerConsumer = bodyHandlers::add;
            }
            if (html) {
               handlerConsumer.accept(new HtmlHandler.Builder().handler(new MetaRefreshHandler.Builder()
                     .processor(fragmented -> new RefreshHandler(
                           queueKey, delayedQueueKey, poolKey, redirectConcurrency, objectAccess(coordsVar),
                           objectAccess(delayedCoordVar),
                           redirectSequenceName, delaySequenceName, objectAccess(tempCoordsVar), Session::currentSequence))));
            }
            if (location) {
               bodyHandlers = Collections.singletonList(conditionalBodyHandler);
            }
         }
         if (!completionHandlers.isEmpty()) {
            completionHandlers = Collections.singletonList(
                  new ConditionalAction.Builder()
                        .condition().stringCondition().fromVar(tempCoordsVar).isSet(false).end()
                        .actions(completionHandlers));
         }
      }

      public HttpResponseHandlersImpl build() {
         return new HttpResponseHandlersImpl(
               toArray(statusHandlers, StatusHandler.Builder::build, StatusHandler[]::new),
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
   }

   private static class StopOnInvalidAction implements Action {
      // prevents some weird serialization incompatibility
      private static final Action INSTANCE = new StopOnInvalidAction();

      @Override
      public void run(Session session) {
         Request request = session.currentRequest();
         if (!request.isValid()) {
            log.info("#{} Stopping session due to invalid response {} on connection {}", session.uniqueId(), request,
                  request.connection());
            session.stop();
         }
      }
   }
}
