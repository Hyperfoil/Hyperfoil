package io.sailrocket.api.http;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import io.netty.buffer.ByteBuf;

public interface HttpResponseHandlers {
   IntConsumer statusHandler();

   Consumer<ByteBuf> dataHandler();

   Runnable endHandler();

   BiConsumer<String, String> headerHandler();

   Consumer<Throwable> exceptionHandler();

   IntConsumer resetHandler();

   Consumer<ByteBuf> rawBytesHandler();

   boolean isCompleted();

   void setCompleted();
}
