package io.hyperfoil.http.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.api.ConnectionConsumer;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpRequest;

/*
 * HttpRequestStep cannot create and obtain connection without blocking and the behaviour is dependent
 * on authority & path so we cannot split it into multiple steps. Therefore we'll track the internal
 * state of execution here.
 */
class HttpRequestContext implements Session.Resource, ConnectionConsumer {
   HttpRequest request;
   HttpConnection connection;
   boolean ready;
   long waitTimestamp = Long.MIN_VALUE;

   @Override
   public void onSessionReset(Session session) {
      reset();
   }

   public HttpConnection connection() {
      return connection;
   }

   public void reset() {
      request = null;
      connection = null;
      ready = false;
      waitTimestamp = Long.MIN_VALUE;
   }

   @Override
   public void accept(HttpConnection connection) {
      assert request.session.executor().inEventLoop();
      this.connection = connection;
      this.ready = true;
      this.request.session.proceed();
   }

   public void startWaiting() {
      if (waitTimestamp == Long.MIN_VALUE) {
         waitTimestamp = System.nanoTime();
      }
   }

   public void stopWaiting() {
      if (waitTimestamp != Long.MIN_VALUE) {
         long blockedTime = System.nanoTime() - waitTimestamp;
         request.statistics().incrementBlockedTime(request.startTimestampMillis(), blockedTime);
      }
   }

   public static final class Key implements Session.ResourceKey<HttpRequestContext> {}
}
