package io.hyperfoil.clustering.webcli;

import java.io.OutputStreamWriter;
import java.util.concurrent.CountDownLatch;

import org.aesh.AeshConsoleRunner;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;

class WebCliContext extends HyperfoilCliContext {
   final String sessionId;
   final OutputStreamWriter inputStream;
   final WebsocketOutputStream outputStream;
   ServerWebSocket webSocket;
   AeshConsoleRunner runner;
   CountDownLatch latch;
   StringBuilder editBenchmark;

   public WebCliContext(Vertx vertx, OutputStreamWriter inputStream, WebsocketOutputStream outputStream, ServerWebSocket webSocket) {
      super(vertx, true);
      this.sessionId = webSocket.query();
      this.inputStream = inputStream;
      this.webSocket = webSocket;
      this.outputStream = outputStream;
   }

   public void reattach(ServerWebSocket webSocket) {
      this.webSocket = webSocket;
      outputStream.reattach(webSocket);
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
