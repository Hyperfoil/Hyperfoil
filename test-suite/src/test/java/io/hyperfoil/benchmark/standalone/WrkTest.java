package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

@Tag("io.hyperfoil.test.Benchmark")
public class WrkTest extends BaseBenchmarkTest {
   protected long unservedDelay = 2000;
   protected double servedRatio = 0.9;

   public WrkTest() {
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return this::serveOrClose;
   }

   private void serveOrClose(HttpServerRequest req) {
      if (servedRatio >= 1.0 || ThreadLocalRandom.current().nextDouble() < servedRatio) {
         req.response().end();
      } else {
         if (unservedDelay > 0) {
            vertx.setTimer(unservedDelay, timer -> req.connection().close());
         } else {
            req.connection().close();
         }
      }
   }

   @Test
   public void testWrk() {
      Wrk cmd = new Wrk();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/foo/bar" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testFailFastWrk() {
      Wrk cmd = new Wrk();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "--latency", "--timeout", "1s",
            "nonExistentHost:" + httpServer.actualPort() + "/foo/bar" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testWrk2() {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/foo/bar" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testWrkReportOutput(@TempDir Path tempDir) {
      Path reportFile = tempDir.resolve(UUID.randomUUID() + ".html");
      assertFalse(reportFile.toFile().exists());
      Wrk2.main(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "--output",
            reportFile.toString(), "localhost:" + httpServer.actualPort() + "/foo/bar" });
      assertTrue(reportFile.toFile().exists());
   }
}
