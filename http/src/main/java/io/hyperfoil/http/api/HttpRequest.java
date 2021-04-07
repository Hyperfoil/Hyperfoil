package io.hyperfoil.http.api;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.http.HttpRequestPool;
import io.netty.buffer.ByteBuf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

public class HttpRequest extends Request {
   public static final Logger log = LogManager.getLogger(HttpRequest.class);

   public HttpResponseHandlers handlers;
   public HttpMethod method;
   public String authority;
   public String path;
   public CacheControl cacheControl = new CacheControl();
   private HttpConnectionPool pool;

   public HttpRequest(Session session) {
      super(session);
   }

   public static HttpRequest ensure(Request request) {
      if (request instanceof HttpRequest) {
         return (HttpRequest) request;
      } else {
         log.error("#{}: Expected HttpRequest, got {}", request.session.uniqueId(), request);
         return null;
      }
   }

   public void start(HttpConnectionPool pool, HttpResponseHandlers handlers, SequenceInstance sequence, Statistics statistics) {
      this.handlers = handlers;
      this.pool = pool;
      start(sequence, statistics);
   }

   public void send(HttpConnection connection,
                    BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                    boolean injectHostHeader,
                    BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      if (session.currentRequest() != null) {
         // Refuse to fire request from other request's handler as the other handlers
         // would have messed up current request in session.
         // Handlers must not block anyway, so this is illegal way to run request
         // and happens only with programmatic configuration in testsuite.
         throw new IllegalStateException(String.format(
               "#{} Invoking request directly from a request handler; current: {}, requested {}",
               session.uniqueId(), session.currentRequest(), this));
      }

      attach(connection);
      connection.attach(pool);
      connection.request(this, headerAppenders, injectHostHeader, bodyGenerator);
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
      this.pool = null;
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
      return super.toString() + " " + method + " " + authority + path;
   }

   @Override
   public void release() {
      if (status() != Status.IDLE) {
         HttpRequestPool.get(session).release(this);
         setIdle();
      }
   }

   public void handleCached() {
      HttpStats.addCacheHit(statistics(), startTimestampMillis());
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
            log.error(new FormattedMessage("{} {} threw an exception when cancelling", session.uniqueId(), this), e);
         } finally {
            exit();
            release();
         }
         session.proceed();
      }
   }
}
