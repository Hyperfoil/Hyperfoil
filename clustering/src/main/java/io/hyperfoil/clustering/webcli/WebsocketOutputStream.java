package io.hyperfoil.clustering.webcli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.http.ServerWebSocket;

class WebsocketOutputStream extends OutputStream implements Callable<Void> {
   private ServerWebSocket webSocket;
   private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
   private ScheduledFuture<Void> future;

   public WebsocketOutputStream(ServerWebSocket webSocket) {
      this.webSocket = webSocket;
   }

   public synchronized void reattach(ServerWebSocket webSocket) {
      this.webSocket = webSocket;
   }

   private void checkCommand(byte[] b, int off, int len) {
      // Commands like __HYPERFOIL_SOMETHING_MAGIC__ start with two underscores.
      // We will flush beforehand to let scripts detect commands at the start of frame
      if (len >= 2 && b[off] == '_' && b[off + 1] == '_') {
         flush();
      }
   }

   @Override
   public synchronized void write(byte[] b) throws IOException {
      checkCommand(b, 0, b.length);
      bytes.write(b);
      scheduleSendTextFrame();
   }

   @Override
   public synchronized void write(byte[] b, int off, int len) {
      checkCommand(b, 0, b.length);
      bytes.write(b, off, len);
      scheduleSendTextFrame();
   }

   @Override
   public synchronized void write(int b) {
      bytes.write(b);
      scheduleSendTextFrame();
   }

   @Override
   public synchronized void flush() {
      if (future != null) {
         future.cancel(false);
      }
      call();
   }

   private void scheduleSendTextFrame() {
      if (future == null) {
         future = WebCLI.SCHEDULED_EXECUTOR.schedule(this, 10, TimeUnit.MILLISECONDS);
      }
   }

   @Override
   public synchronized Void call() {
      webSocket.writeTextMessage(bytes.toString(StandardCharsets.UTF_8));
      bytes.reset();
      future = null;
      return null;
   }
}
