package io.hyperfoil.api.connection;

import java.util.function.Function;

import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.http.CacheControl;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.buffer.ByteBuf;

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

   /**
    * Processors for HTTP requests.
    */
   @IncludeBuilders(
         @IncludeBuilders.Conversion(from = Request.ProcessorBuilder.class, adapter = BuilderConverter.class)
   )
   public interface ProcessorBuilder extends Processor.Builder<HttpRequest, ProcessorBuilder> {}

   public static class BuilderConverter implements Function<Request.ProcessorBuilder, HttpRequest.ProcessorBuilder> {
      @Override
      public ProcessorBuilder apply(Request.ProcessorBuilder processorBuilder) {
         return () -> new ProcessorAdapter(processorBuilder.build());
      }
   }

   public static class ProcessorAdapter implements Processor<HttpRequest>, ResourceUtilizer {
      private final Processor<Request> delegate;

      public ProcessorAdapter(Processor<Request> delegate) {
         this.delegate = delegate;
      }

      @Override
      public void before(HttpRequest request) {
         delegate.before(request);
      }

      @Override
      public void process(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
         delegate.process(request, data, offset, length, isLastPart);
      }

      @Override
      public void after(HttpRequest request) {
         delegate.after(request);
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, delegate);
      }
   }
}
