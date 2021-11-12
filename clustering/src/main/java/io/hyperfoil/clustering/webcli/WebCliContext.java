package io.hyperfoil.clustering.webcli;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.aesh.AeshConsoleRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.model.Run;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;

class WebCliContext extends HyperfoilCliContext {
   private static final Logger log = LogManager.getLogger(WebCliContext.class);

   final String sessionId;
   final OutputStreamWriter inputStream;
   final WebsocketOutputStream outputStream;
   ScheduledFuture<?> runCompletionFuture;
   ServerWebSocket webSocket;
   AeshConsoleRunner runner;
   CountDownLatch latch;
   StringBuilder editBenchmark;
   int binaryLength;
   ByteArrayOutputStream binaryContent;
   String prevId = null;
   String notifiedId = null;

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
      outputStream.writeSingleBinary(buffer);
   }

   void startNotifications() {
      if (runCompletionFuture != null) {
         runCompletionFuture.cancel(false);
      }
      runCompletionFuture = executor().scheduleAtFixedRate(this::checkRunCompletion, 0, 15, TimeUnit.SECONDS);
   }

   private void checkRunCompletion() {
      RestClient client = client();
      if (client == null) {
         return;
      }
      Run current = client.run("last").get();
      String fetchRun = null;
      synchronized (this) {
         if (notifiedId == null) {
            prevId = this.notifiedId = current.id;
            return;
         }
         if (current.completed && this.notifiedId.compareTo(current.id) < 0) {
            notifyRunCompleted(current);
         } else if (!prevId.equals(current.id) && this.notifiedId.compareTo(prevId) < 0) {
            fetchRun = prevId;
         }
      }
      prevId = current.id;
      if (fetchRun != null) {
         notifyRunCompleted(client.run(fetchRun).get());
      }
   }

   @Override
   public synchronized void notifyRunCompleted(Run run) {
      if (notifiedId == null || notifiedId.compareTo(run.id) < 0) {
         notifiedId = run.id;
      } else {
         return;
      }

      StringBuilder sb = new StringBuilder("__HYPERFOIL_NOTIFICATION__");
      // title
      sb.append("Run ").append(run.id).append(" (").append(run.benchmark).append(") has finished").append('\n');
      // body
      if (run.cancelled) {
         sb.append("The run was cancelled.\n");
      }
      if (run.started != null && run.terminated != null) {
         String prettyDuration = Duration.between(run.started.toInstant(), run.terminated.toInstant())
               .toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
         sb.append("Total duration: ").append(prettyDuration).append('\n');
      }
      if (run.errors != null && !run.errors.isEmpty()) {
         sb.append("Errors (").append(run.errors.size()).append("):\n");
         run.errors.stream().limit(10).forEach(e -> sb.append(e).append('\n'));
         if (run.errors.size() > 10) {
            sb.append("... (further errors omitted)");
         }
      }
      outputStream.writeSingleText(sb.toString());
   }
}
