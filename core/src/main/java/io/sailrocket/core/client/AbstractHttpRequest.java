package io.sailrocket.core.client;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.api.http.HttpResponseHandlers;

public abstract class AbstractHttpRequest implements HttpRequest, HttpResponseHandlers {
   protected IntConsumer statusHandler;
   protected Consumer<ByteBuf> dataHandler;
   protected Runnable endHandler;
   protected BiConsumer<String, String> headerHandler;
   protected Consumer<Throwable> exceptionHandler;
   protected IntConsumer resetHandler;
   protected Consumer<ByteBuf> rawBytesHandler;
   protected boolean completed;

   @Override
   public HttpRequest statusHandler(IntConsumer handler) {
       statusHandler = handler;
       return this;
   }

   @Override
   public HttpRequest headerHandler(BiConsumer<String, String> handler) {
       this.headerHandler = handler;
       return this;
   }

   @Override
   public HttpRequest resetHandler(IntConsumer handler) {
       resetHandler = handler;
       return this;
   }

   @Override
   public HttpRequest bodyPartHandler(Consumer<ByteBuf> handler) {
       this.dataHandler = handler;
       return this;
   }

   @Override
   public HttpRequest endHandler(Runnable handler) {
       endHandler = handler;
       return this;
   }

   @Override
   public HttpRequest exceptionHandler(Consumer<Throwable> handler) {
       exceptionHandler = handler;
       return this;
   }

   @Override
   public HttpRequest rawBytesHandler(Consumer<ByteBuf> handler) {
       rawBytesHandler = handler;
       return this;
   }

   @Override
   public IntConsumer statusHandler() {
      return statusHandler;
   }

   @Override
   public Consumer<ByteBuf> dataHandler() {
      return dataHandler;
   }

   @Override
   public Runnable endHandler() {
      return endHandler;
   }

   @Override
   public BiConsumer<String, String> headerHandler() {
      return headerHandler;
   }

   @Override
   public Consumer<Throwable> exceptionHandler() {
      return exceptionHandler;
   }

   @Override
   public IntConsumer resetHandler() {
      return resetHandler;
   }

   @Override
   public Consumer<ByteBuf> rawBytesHandler() {
      return rawBytesHandler;
   }

   @Override
   public boolean isCompleted() {
      return completed;
   }

   @Override
   public void setCompleted() {
      completed = true;
   }
}
