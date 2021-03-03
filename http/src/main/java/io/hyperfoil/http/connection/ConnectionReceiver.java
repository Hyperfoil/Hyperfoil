package io.hyperfoil.http.connection;

import java.util.function.BiConsumer;

import io.hyperfoil.http.api.HttpConnection;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public interface ConnectionReceiver extends BiConsumer<HttpConnection, Throwable>, GenericFutureListener<Future<Void>> {
   @Override
   default void operationComplete(Future<Void> future) {
      if (!future.isSuccess()) {
         accept(null, future.cause());
      }
   }
}
