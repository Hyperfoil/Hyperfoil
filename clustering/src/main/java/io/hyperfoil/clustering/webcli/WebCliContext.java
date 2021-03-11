package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;

class WebCliContext extends HyperfoilCliContext {
   private final ServerWebSocket webSocket;
   private final WebsocketOutputStream outputStream;
   CountDownLatch latch;
   StringBuilder editBenchmark;

   public WebCliContext(Vertx vertx, ServerWebSocket webSocket, WebsocketOutputStream outputStream) {
      super(vertx, true);
      this.webSocket = webSocket;
      this.outputStream = outputStream;
   }

   @Override
   public String interruptKey() {
      return "Escape";
   }

   @Override
   public synchronized Pager createPager(String pager) {
      latch = new CountDownLatch(1);
      return new WebPager();
   }

   public void sendBinaryMessage(Buffer buffer) {
      // We need to flush output to keep ordering of text and binary frames
      outputStream.flush();
      webSocket.writeBinaryMessage(buffer);
   }
}
