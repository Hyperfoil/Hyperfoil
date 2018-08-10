package io.sailrocket.core.client;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.HttpResponse;
import io.sailrocket.spi.HttpHeader;

public abstract class AbstractHttpRequest implements HttpRequest {
   protected IntConsumer statusHandler;
   protected Consumer<byte[]> dataHandler;
   protected Consumer<HttpResponse> endHandler;
   protected Consumer<HttpHeader> headerHandler;
   protected Consumer<Throwable> exceptionHandler;
   protected IntConsumer resetHandler;

   @Override
   public HttpRequest statusHandler(IntConsumer handler) {
       statusHandler = handler;
       return this;
   }

   @Override
   public HttpRequest headerHandler(Consumer<HttpHeader> handler) {
       this.headerHandler = handler;
       return this;
   }

   @Override
   public HttpRequest resetHandler(IntConsumer handler) {
       resetHandler = handler;
       return this;
   }

   @Override
   public HttpRequest bodyHandler(Consumer<byte[]> handler) {
       this.dataHandler = handler;
       return this;
   }

   @Override
   public HttpRequest endHandler(Consumer<HttpResponse> handler) {
       endHandler = handler;
       return this;
   }

   @Override
   public HttpRequest exceptionHandler(Consumer<Throwable> handler) {
       exceptionHandler = handler;
       return this;
   }
}
