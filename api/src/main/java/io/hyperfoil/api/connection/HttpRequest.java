package io.hyperfoil.api.connection;

import io.hyperfoil.api.http.CacheControl;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;

public class HttpRequest extends Request {
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
      handlers.handleThrowable(this, throwable);
   }

   @Override
   public String toString() {
      return method + " " + authority + path;
   }

}
