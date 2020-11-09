package io.hyperfoil.core.handlers;

import java.util.Objects;
import java.util.function.Supplier;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableFunction;
import io.netty.buffer.ByteBuf;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Redirect {
   private static final Logger log = LoggerFactory.getLogger(Redirect.class);
   private static final boolean trace = log.isTraceEnabled();

   public static class StatusHandler extends BaseDelegatingStatusHandler {
      private final Access methodVar;

      public StatusHandler(Access methodVar, io.hyperfoil.api.http.StatusHandler[] handlers) {
         super(handlers);
         this.methodVar = methodVar;
      }

      @Override
      public void handleStatus(HttpRequest request, int status) {
         switch (status) {
            case 301:
            case 302:
            case 303:
               methodVar.setObject(request.session, HttpMethod.GET);
               break;
            case 307:
            case 308:
               methodVar.setObject(request.session, request.method);
               break;
            default:
               methodVar.unset(request.session);
               super.handleStatus(request, status);
         }
      }

      @Override
      public void reserve(Session session) {
         super.reserve(session);
         methodVar.declareObject(session);
      }

      public static class Builder extends BaseDelegatingStatusHandler.Builder<Builder> {
         private Object methodVar;

         public Builder methodVar(Object methodVar) {
            this.methodVar = methodVar;
            return this;
         }

         @Override
         public StatusHandler build() {
            return new StatusHandler(SessionFactory.access(methodVar), buildHandlers());
         }
      }
   }

   public static class Coords {
      public HttpMethod method;
      public CharSequence location;
      public SequenceInstance originalSequence;

      public Coords reset() {
         method = null;
         location = null;
         originalSequence = null;
         return this;
      }

      @Override
      public String toString() {
         return method + " " + location;
      }
   }

   public static class LocationRecorder implements HeaderHandler, ResourceUtilizer {
      private static final String LOCATION = "location";

      private final int concurrency;
      private final LimitedPoolResource.Key<Coords> poolKey;
      private final Session.ResourceKey<Queue> queueKey;
      private final Access inputVar;
      private final Access outputVar;
      private final String sequence;
      private final SerializableFunction<Session, SequenceInstance> originalSequenceSupplier;

      public LocationRecorder(int concurrency, LimitedPoolResource.Key<Coords> poolKey, Session.ResourceKey<Queue> queueKey, Access inputVar, Access outputVar, String sequence, SerializableFunction<Session, SequenceInstance> originalSequenceSupplier) {
         this.concurrency = concurrency;
         this.poolKey = poolKey;
         this.queueKey = queueKey;
         this.inputVar = inputVar;
         this.outputVar = outputVar;
         this.sequence = sequence;
         this.originalSequenceSupplier = originalSequenceSupplier;
      }

      @Override
      public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
         if (Util.regionMatchesIgnoreCase(header, 0, LOCATION, 0, LOCATION.length())) {
            Session session = request.session;
            ObjectVar var = (ObjectVar) inputVar.getVar(session);
            if (!var.isSet()) {
               return;
            }
            Object obj = var.objectValue(session);
            if (obj instanceof HttpMethod) {
               LimitedPoolResource<Coords> pool = session.getResource(poolKey);
               Coords coords = pool.acquire();
               coords.method = (HttpMethod) obj;
               coords.location = value;
               coords.originalSequence = originalSequenceSupplier.apply(request.session);
               var.set(coords);
               Queue queue = session.getResource(queueKey);
               queue.push(session, coords);
            } else if (obj instanceof Coords) {
               log.error("Duplicate location header: previously got {}, now {}. Ignoring the second match.", ((Coords) obj).location, value);
            } else {
               log.error("Unexpected value in methodVar: {}", obj);
            }
         }
      }

      @Override
      public void afterHeaders(HttpRequest request) {
         Session.Var var = inputVar.getVar(request.session);
         if (var.isSet() && !(var.objectValue(request.session) instanceof Coords)) {
            log.error("Location header is missing in response from {} {}{}!", request.method, request.authority, request.path);
            request.markInvalid();
         }
      }

      @Override
      public void reserve(Session session) {
         outputVar.declareObject(session);
         if (!outputVar.isSet(session)) {
            outputVar.setObject(session, ObjectVar.newArray(session, concurrency));
         }
         session.declareResource(poolKey, () -> LimitedPoolResource.create(concurrency, Coords.class, Coords::new), true);
         session.declareResource(queueKey, () -> new Queue(outputVar, concurrency, concurrency, sequence, null));
      }

      public static class Builder implements HeaderHandler.Builder {
         private Session.ResourceKey<Queue> queueKey;
         private Object inputVar, outputVar;
         private LimitedPoolResource.Key<Coords> poolKey;
         private int concurrency;
         private String sequence;
         private Supplier<SerializableFunction<Session, SequenceInstance>> originalSequenceSupplier;

         public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
         }

         public Builder inputVar(Object inputVar) {
            this.inputVar = inputVar;
            return this;
         }

         public Builder outputVar(Object outputVar) {
            this.outputVar = outputVar;
            return this;
         }

         public Builder queueKey(Session.ResourceKey<Queue> queueKey) {
            this.queueKey = queueKey;
            return this;
         }

         public Builder poolKey(LimitedPoolResource.Key<Coords> poolKey) {
            this.poolKey = poolKey;
            return this;
         }

         public Builder sequence(String sequence) {
            this.sequence = sequence;
            return this;
         }

         public Builder originalSequenceSupplier(Supplier<SerializableFunction<Session, SequenceInstance>> supplier) {
            this.originalSequenceSupplier = supplier;
            return this;
         }

         @Override
         public LocationRecorder build() {
            if (Objects.equals(inputVar, outputVar)) {
               throw new BenchmarkDefinitionException("Input (" + inputVar + ") and output (" + outputVar + ") variables must differ");
            }
            assert inputVar != null;
            assert outputVar != null;
            assert sequence != null;
            assert poolKey != null;
            return new LocationRecorder(concurrency, poolKey, queueKey, SessionFactory.access(inputVar), SessionFactory.access(outputVar), this.sequence, originalSequenceSupplier.get());
         }
      }
   }

   public static class GetMethod implements SerializableFunction<Session, HttpMethod> {
      private final Access coordVar;

      public GetMethod(Access coordVar) {
         this.coordVar = coordVar;
      }

      @Override
      public HttpMethod apply(Session session) {
         Coords coords = (Coords) coordVar.getObject(session);
         return coords.method;
      }
   }

   public static class GetLocation implements SerializableFunction<Session, String> {
      private final Access coordVar;

      public GetLocation(Access coordVar) {
         this.coordVar = coordVar;
      }

      @Override
      public String apply(Session session) {
         Coords coords = (Coords) coordVar.getObject(session);
         return coords.location.toString();
      }
   }

   public static class Complete implements Action {
      private final LimitedPoolResource.Key<Coords> poolKey;
      private final Session.ResourceKey<Queue> queueKey;
      private final Access coordVar;

      public Complete(LimitedPoolResource.Key<Coords> poolKey, Session.ResourceKey<Queue> queueKey, Access coordVar) {
         this.poolKey = poolKey;
         this.queueKey = queueKey;
         this.coordVar = coordVar;
      }

      @Override
      public void run(Session session) {
         LimitedPoolResource<Coords> pool = session.getResource(poolKey);
         ObjectVar var = (ObjectVar) coordVar.getVar(session);
         Coords coords = (Coords) var.get();
         if (trace) {
            log.trace("#{} releasing {} from {}[{}]", session.uniqueId(), coords, coordVar, session.currentSequence().index());
         }
         pool.release(coords.reset());
         var.set(null);
         var.unset();
         session.getResource(queueKey).consumed(session);
      }
   }

   private static SequenceInstance pushCurrentSequence(Session session, Access coordsVar) {
      Coords coords = (Coords) coordsVar.getObject(session);
      SequenceInstance currentSequence = session.currentSequence();
      session.currentSequence(coords.originalSequence);
      Request request = session.currentRequest();
      if (request != null) {
         request.unsafeEnterSequence(coords.originalSequence);
      }
      return currentSequence;
   }

   private static void popCurrentSequence(Session session, SequenceInstance currentSequence) {
      session.currentSequence(currentSequence);
      Request request = session.currentRequest();
      if (request != null) {
         request.unsafeEnterSequence(currentSequence);
      }
   }

   public static class WrappingStatusHandler extends BaseDelegatingStatusHandler {
      private final Access coordsVar;

      public WrappingStatusHandler(io.hyperfoil.api.http.StatusHandler[] handlers, Access coordsVar) {
         super(handlers);
         this.coordsVar = coordsVar;
      }

      @Override
      public void handleStatus(HttpRequest request, int status) {
         SequenceInstance currentSequence = pushCurrentSequence(request.session, coordsVar);
         try {
            super.handleStatus(request, status);
         } finally {
            popCurrentSequence(request.session, currentSequence);
         }
      }

      public static class Builder extends BaseDelegatingStatusHandler.Builder<Builder> {
         private Object coordsVar;

         public Builder coordsVar(Object coordsVar) {
            this.coordsVar = coordsVar;
            return this;
         }

         @Override
         public WrappingStatusHandler build() {
            return new WrappingStatusHandler(buildHandlers(), SessionFactory.sequenceScopedAccess(coordsVar));
         }
      }
   }

   public static class WrappingHeaderHandler extends BaseDelegatingHeaderHandler {
      private final Access coordsVar;

      public WrappingHeaderHandler(HeaderHandler[] handlers, Access coordsVar) {
         super(handlers);
         this.coordsVar = coordsVar;
      }

      @Override
      public void beforeHeaders(HttpRequest request) {
         SequenceInstance currentSequence = pushCurrentSequence(request.session, coordsVar);
         try {
            super.beforeHeaders(request);
         } finally {
            popCurrentSequence(request.session, currentSequence);
         }
      }

      @Override
      public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
         SequenceInstance currentSequence = pushCurrentSequence(request.session, coordsVar);
         try {
            super.handleHeader(request, header, value);
         } finally {
            popCurrentSequence(request.session, currentSequence);
         }
      }

      @Override
      public void afterHeaders(HttpRequest request) {
         SequenceInstance currentSequence = pushCurrentSequence(request.session, coordsVar);
         try {
            super.afterHeaders(request);
         } finally {
            popCurrentSequence(request.session, currentSequence);
         }
      }

      public static class Builder extends BaseDelegatingHeaderHandler.Builder<Builder> {
         private Object coordVar;

         public Builder coordVar(Object coordVar) {
            this.coordVar = coordVar;
            return this;
         }

         @Override
         public WrappingHeaderHandler build() {
            return new WrappingHeaderHandler(buildHandlers(), SessionFactory.sequenceScopedAccess(coordVar));
         }
      }
   }

   public static class WrappingProcessor extends MultiProcessor {
      private final Access coordVar;

      public WrappingProcessor(Processor[] processors, Access coordVar) {
         super(processors);
         this.coordVar = coordVar;
      }

      @Override
      public void before(Session session) {
         SequenceInstance currentSequence = pushCurrentSequence(session, coordVar);
         try {
            super.before(session);
         } finally {
            popCurrentSequence(session, currentSequence);
         }
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         SequenceInstance currentSequence = pushCurrentSequence(session, coordVar);
         try {
            super.process(session, data, offset, length, isLastPart);
         } finally {
            popCurrentSequence(session, currentSequence);
         }
      }

      @Override
      public void after(Session session) {
         SequenceInstance currentSequence = pushCurrentSequence(session, coordVar);
         try {
            super.after(session);
         } finally {
            popCurrentSequence(session, currentSequence);
         }
      }

      public static class Builder extends MultiProcessor.Builder<Builder> {
         private Object coordVar;

         public Builder coordVar(Object coordVar) {
            this.coordVar = coordVar;
            return this;
         }

         @Override
         public WrappingProcessor build(boolean fragmented) {
            return new WrappingProcessor(buildProcessors(fragmented), SessionFactory.sequenceScopedAccess(coordVar));
         }
      }
   }

   public static class WrappingAction extends BaseDelegatingAction {
      private final Access coordVar;

      public WrappingAction(Action[] actions, Access coordVar) {
         super(actions);
         this.coordVar = coordVar;
      }

      @Override
      public void run(Session session) {
         SequenceInstance currentSequence = pushCurrentSequence(session, coordVar);
         try {
            super.run(session);
         } finally {
            popCurrentSequence(session, currentSequence);
         }
      }

      public static class Builder extends BaseDelegatingAction.Builder<Builder> {
         private Object coordVar;

         public Builder coordVar(Object coordVar) {
            this.coordVar = coordVar;
            return this;
         }

         @Override
         public WrappingAction build() {
            return new WrappingAction(buildActions(), SessionFactory.sequenceScopedAccess(coordVar));
         }
      }
   }
}
