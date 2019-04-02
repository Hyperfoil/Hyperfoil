package io.hyperfoil.api.connection;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;

public class HttpRequest extends Request {
   public HttpResponseHandlers handlers;
   public HttpMethod method;
   public String baseUrl;
   public String path;

   public HttpRequest(Session session) {
      super(session);
   }

   public void start(HttpResponseHandlers handlers, SequenceInstance sequence, Statistics statistics) {
      this.handlers = handlers;
      start(sequence, statistics);
   }

   public HttpResponseHandlers handlers() {
      return handlers;
   }

   @Override
   protected void handleThrowable(Throwable throwable) {
      handlers.handleThrowable(this, throwable);
   }

   @ServiceLoadedFactory.Include(Request.ProcessorBuilderFactory.class)
   public interface ProcessorBuilderFactory extends ServiceLoadedFactory<Processor.Builder<HttpRequest>> {}
}
