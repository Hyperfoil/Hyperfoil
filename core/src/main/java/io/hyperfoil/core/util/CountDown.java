package io.hyperfoil.core.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class CountDown {
   private Handler<AsyncResult<Void>> handler;
   private int value;

   public CountDown(Handler<AsyncResult<Void>> handler, int initialValue) {
      if (initialValue <= 0) {
         throw new IllegalArgumentException();
      }
      this.handler = handler;
      this.value = initialValue;
   }

   public CountDown(int initialValue) {
      this(null, initialValue);
   }

   public CountDown setHandler(Handler<AsyncResult<Void>> handler) {
      if (this.handler != null) {
         throw new IllegalStateException();
      } else if (handler == null) {
         throw new IllegalArgumentException();
      }
      this.handler = handler;
      return this;
   }

   public void increment() {
      if (value < 0) {
         throw new IllegalStateException();
      }
      ++value;
   }

   public void countDown() {
      if (value <= 0) {
         throw new IllegalStateException();
      }
      if (--value == 0) {
         value = -1;
         handler.handle(Future.succeededFuture());
      }
   }
}
