package io.hyperfoil.internal;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;

public interface WebCliProvider {
   WebCli create(Vertx vertx);

   interface WebCli extends Handler<ServerWebSocket> {
      void setConnectionOptions(String hostname, int port, boolean ssl);
   }
}
