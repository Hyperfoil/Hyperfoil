package io.hyperfoil.http;

import io.hyperfoil.api.collection.LimitedPool;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.http.api.HttpRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequestPool extends LimitedPoolResource<HttpRequest> {
   private static final Logger log = LoggerFactory.getLogger(HttpRequestPool.class);
   private static final boolean trace = log.isTraceEnabled();
   public static final Session.ResourceKey<LimitedPoolResource<HttpRequest>> KEY = new Key<>();

   public HttpRequestPool(Scenario scenario, Session session) {
      super(scenario.maxRequests(), HttpRequest.class, () -> new HttpRequest(session));
   }

   public static LimitedPool<HttpRequest> get(Session session) {
      return session.getResource(KEY);
   }

   @Override
   public void onSessionReset(Session session) {
      if (!isFull()) {
         // We can't guarantee that requests will be back in session's requestPool when it terminates
         // because if the requests did timeout (calling handlers and eventually letting the session terminate)
         // it might still be held in the connection.
         for (HttpRequest request : (HttpRequest[]) originalObjects) {
            // We won't issue the warning for invalid requests because these are likely not in flight anymore
            // and we are stopping the session exactly due to the invalid request.
            if (!request.isCompleted() && request.isValid()) {
               log.warn("#{} Session completed with requests in-flight!", session.uniqueId());
               break;
            }
         }
         cancelRequests();
      }
      super.onSessionReset(session);
   }

   private void cancelRequests() {
      // We need to close all connections used to ongoing requests, despite these might
      // carry requests from independent phases/sessions
      for (HttpRequest request : (HttpRequest[]) originalObjects) {
         if (!request.isCompleted()) {
            // When one of the handlers calls Session.stop() it may terminate the phase completely,
            // sending stats before recording the invalid request in HttpResponseHandlersImpl.handleEnd().
            // That's why we record it here instead and mark the request as completed (to recording the stats twice)
            if (!request.isValid()) {
               request.statistics().addInvalid(request.startTimestampMillis());
            }
            if (trace) {
               log.trace("Canceling request {} to {}", request, request.connection());
            }
            request.setCompleting();
            if (request.connection() != null) {
               request.connection().close();
            }
            if (!request.isCompleted()) {
               // Connection.close() cancels everything in flight but if this is called
               // from handleEnd() the request is not in flight anymore
               log.trace("#{} Connection close did not complete the request.", request.session != null ? request.session.uniqueId() : 0);
               request.setCompleted();
               request.release();
            }
         }
      }
   }
}
