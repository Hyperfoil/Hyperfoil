package io.hyperfoil.api.connection;

import io.hyperfoil.api.http.CacheControl;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.api.statistics.Statistics;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HttpRequest extends Request {
   public static final Logger log = LoggerFactory.getLogger(HttpRequest.class);

   public HttpResponseHandlers handlers;
   public HttpMethod method;
   public String authority;
   public String path;
   public CacheControl cacheControl = new CacheControl();

   public HttpRequest(Session session) {
      super(session);
   }

   public void start(HttpResponseHandlers handlers, SequenceInstance sequence, Statistics statistics) {
      this.handlers = handlers;
      start(sequence, statistics);
   }

   @Override
   public HttpConnection connection() {
      return (HttpConnection) super.connection();
   }

   @Override
   public void setCompleted() {
      super.setCompleted();
      this.handlers = null;
      this.method = null;
      this.authority = null;
      this.path = null;
      cacheControl.reset();
   }

   public HttpResponseHandlers handlers() {
      return handlers;
   }

   @Override
   protected void handleThrowable(Throwable throwable) {
      enter();
      try {
         handlers.handleThrowable(this, throwable);
      } finally {
         exit();
      }
      session.proceed();
   }

   @Override
   public String toString() {
      return "(" + status() + ") " + method + " " + authority + path;
   }

   @Override
   public void release() {
      if (status() != Status.IDLE) {
         session.httpRequestPool().release(this);
         setIdle();
      }
   }

   public void handleCached() {
      statistics().addCacheHit(startTimestampMillis());
      enter();
      try {
         handlers.handleEnd(this, false);
      } catch (SessionStopException e) {
         // ignore, other exceptions would propagate
      } finally {
         exit();
         release();
      }
      session.proceed();
   }

   public void cancel(Throwable cause) {
      if (isRunning()) {
         enter();
         try {
            handlers.handleThrowable(this, cause);
         } catch (SessionStopException e) {
            // ignore - the cancelled request decided to stop its session, too
         } catch (Exception e) {
            log.error("{} {} threw an exception when cancelling", e, session.uniqueId(), this);
         } finally {
            exit();
            release();
         }
         session.proceed();
      }
   }
}
