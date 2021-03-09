package io.hyperfoil.clustering.webcli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.aesh.AeshConsoleRunner;
import org.aesh.command.Command;
import org.aesh.command.registry.CommandRegistryException;
import org.aesh.readline.ReadlineConsole;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.tty.Signal;

import io.hyperfoil.cli.HyperfoilCli;
import io.hyperfoil.cli.commands.Connect;
import io.hyperfoil.cli.commands.Edit;
import io.hyperfoil.cli.commands.Exit;
import io.hyperfoil.cli.commands.Export;
import io.hyperfoil.cli.commands.Oc;
import io.hyperfoil.cli.commands.Report;
import io.hyperfoil.cli.commands.RunLocal;
import io.hyperfoil.cli.commands.StartLocal;
import io.hyperfoil.cli.commands.Upload;
import io.hyperfoil.client.RestClient;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class WebCLI extends HyperfoilCli implements Handler<ServerWebSocket> {
   private static final Logger log = LoggerFactory.getLogger(WebCLI.class);
   private static final String EDITS_BEGIN = "__HYPERFOIL_EDITS_BEGIN__\n";
   private static final String EDITS_END = "__HYPERFOIL_EDITS_END__\n";
   private static final String INTERRUPT_SIGNAL = "__HYPERFOIL_INTERRUPT_SIGNAL__";
   private static final String AUTH_TOKEN = "__HYPERFOIL_AUTH_TOKEN__";
   private static final String SET_BENCHMARK = "__HYPERFOIL_SET_BENCHMARK__";

   private final Vertx vertx;
   private int port = 8090;

   public WebCLI(Vertx vertx) {
      this.vertx = vertx;
   }

   @Override
   public void handle(ServerWebSocket event) {
      PipedOutputStream pos = new PipedOutputStream();
      PipedInputStream pis;
      try {
         pis = new PipedInputStream(pos);
      } catch (IOException e) {
         log.error("Failed to create input stream", e);
         event.close();
         return;
      }
      OutputStreamWriter cmdInput = new OutputStreamWriter(pos);
      OutputStream stream = new WebsocketOutputStream(event);

      WebCliContext context = new WebCliContext(vertx);
      context.setClient(new RestClient(context.vertx(), "localhost", port, false, false, null));
      context.setOnline(true);
      AeshConsoleRunner runner;
      try {
         var settingsBuilder = settingsBuilder(context);
         settingsBuilder.inputStream(pis)
               .persistHistory(false)
               .historySize(Integer.MAX_VALUE)
               .outputStreamError(new PrintStream(stream))
               .outputStream(new PrintStream(stream));

         runner = configureRunner(context, settingsBuilder.build(), null);
         Thread cliThread = new Thread(runner::start, "webcli-" + event.remoteAddress());
         cliThread.setDaemon(true);
         event.closeHandler(nil -> runner.stop());
         cliThread.start();
      } catch (CommandRegistryException e) {
         throw new IllegalStateException(e);
      }

      event.writeTextMessage("Welcome to Hyperfoil! Type 'help' for commands overview.\n");
      event.textMessageHandler(msg -> {
         synchronized (context) {
            if (context.editBenchmark != null) {
               int editsEnd = msg.indexOf(EDITS_END);
               if (editsEnd >= 0) {
                  context.editBenchmark.append(msg, 0, editsEnd);
                  context.latch.countDown();
               } else {
                  context.editBenchmark.append(msg);
               }
               return;
            } else if (msg.equals(INTERRUPT_SIGNAL)) {
               if (context.latch != null) {
                  context.latch.countDown();
               } else {
                  TerminalConnection connection = getConnection(runner);
                  if (connection != null) {
                     connection.getTerminal().raise(Signal.INT);
                  }
               }
               return;
            } else if (msg.startsWith(EDITS_BEGIN)) {
               context.editBenchmark = new StringBuilder();
               context.editBenchmark.append(msg.substring(EDITS_BEGIN.length()));
               return;
            } else if (msg.startsWith(AUTH_TOKEN)) {
               context.client().setToken(msg.substring(AUTH_TOKEN.length()));
               return;
            } else if (msg.startsWith(SET_BENCHMARK)) {
               context.setServerBenchmark(context.client().benchmark(msg.substring(SET_BENCHMARK.length())));
               return;
            }
         }
         try {
            cmdInput.write(msg);
            cmdInput.flush();
         } catch (IOException e) {
            log.error("Failed to write '{}' to Aesh input", e, msg);
            event.close();
         }
      });
   }

   private TerminalConnection getConnection(AeshConsoleRunner runner) {
      try {
         Field consoleField = AeshConsoleRunner.class.getDeclaredField("console");
         consoleField.setAccessible(true);
         ReadlineConsole console = (ReadlineConsole) consoleField.get(runner);
         Field connectionField = ReadlineConsole.class.getDeclaredField("connection");
         connectionField.setAccessible(true);
         return (TerminalConnection) connectionField.get(console);
      } catch (NoSuchFieldException | IllegalAccessException e) {
         return null;
      }
   }

   @Override
   protected List<Class<? extends Command>> getCommands() {
      ArrayList<Class<? extends Command>> commands = new ArrayList<>(super.getCommands());
      commands.remove(Connect.class);
      commands.remove(Edit.class);
      commands.remove(Exit.class);
      commands.remove(Export.class);
      commands.remove(Oc.class);
      commands.remove(Report.class);
      commands.remove(RunLocal.class);
      commands.remove(StartLocal.class);
      commands.remove(Upload.class);
      commands.add(WebEdit.class);
      commands.add(WebExport.class);
      commands.add(WebReport.class);
      commands.add(WebUpload.class);
      return commands;
   }

   public void setPort(int port) {
      this.port = port;
   }

   private static class WebsocketOutputStream extends OutputStream {
      private final ServerWebSocket event;

      public WebsocketOutputStream(ServerWebSocket event) {
         this.event = event;
      }

      @Override
      public void write(byte[] b) {
         event.writeTextMessage(new String(b, StandardCharsets.UTF_8));
      }

      @Override
      public void write(byte[] b, int off, int len) {
         event.writeTextMessage(new String(b, off, len, StandardCharsets.UTF_8));
      }

      @Override
      public void write(int b) {
         event.writeTextMessage(String.valueOf((char) b));
      }
   }

}