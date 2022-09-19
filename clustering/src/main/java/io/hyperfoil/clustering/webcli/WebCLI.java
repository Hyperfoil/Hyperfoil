package io.hyperfoil.clustering.webcli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.aesh.AeshConsoleRunner;
import org.aesh.command.Command;
import org.aesh.command.registry.CommandRegistryException;
import org.aesh.readline.ReadlineConsole;
import org.aesh.readline.terminal.impl.ExternalTerminal;
import org.aesh.readline.terminal.impl.LineDisciplineTerminal;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.tty.Signal;

import io.hyperfoil.cli.HyperfoilCli;
import io.hyperfoil.cli.commands.Connect;
import io.hyperfoil.cli.commands.Edit;
import io.hyperfoil.cli.commands.Exit;
import io.hyperfoil.cli.commands.Export;
import io.hyperfoil.cli.commands.Report;
import io.hyperfoil.cli.commands.Run;
import io.hyperfoil.cli.commands.RunLocal;
import io.hyperfoil.cli.commands.StartLocal;
import io.hyperfoil.cli.commands.Upload;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.impl.Util;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;

import org.aesh.terminal.tty.Size;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

public class WebCLI extends HyperfoilCli implements Handler<ServerWebSocket> {
   private static final Logger log = LogManager.getLogger(WebCLI.class);
   private static final String EDITS_BEGIN = "__HYPERFOIL_EDITS_BEGIN__\n";
   private static final String EDITS_END = "__HYPERFOIL_EDITS_END__\n";
   private static final String INTERRUPT_SIGNAL = "__HYPERFOIL_INTERRUPT_SIGNAL__";
   private static final String AUTH_TOKEN = "__HYPERFOIL_AUTH_TOKEN__";
   private static final String SET_BENCHMARK = "__HYPERFOIL_SET_BENCHMARK__";
   private static final String SET_TERM_SIZE = "__HYPERFOIL_SET_TERM_SIZE__";
   private static final String SEND_NOTIFICATIONS = "__HYPERFOIL_SEND_NOTIFICATIONS__";
   private static final String FILE_TRANSFER = "__HYPERFOIL_FILE_TRANSFER__";
   private static final long SESSION_TIMEOUT = 60000;

   static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(1, Util.daemonThreadFactory("webcli-timer"));

   private final Vertx vertx;
   private final ConcurrentMap<String, WebCliContext> contextMap = new ConcurrentHashMap<>();
   private final ConcurrentMap<String, ClosedContext> closedRunners = new ConcurrentHashMap<>();
   private int port = 8090;
   private boolean ssl = false;

   public WebCLI(Vertx vertx) {
      this.vertx = vertx;
   }

   @Override
   public void handle(ServerWebSocket webSocket) {
      String sessionId = webSocket.query();
      if (sessionId == null || sessionId.isEmpty()) {
         throw new IllegalStateException();
      }
      ClosedContext closed = closedRunners.remove(sessionId);
      if (closed != null) {
         closed.future.cancel(false);
      }
      WebCliContext context = contextMap.compute(sessionId, (sid, existing) -> {
         if (existing == null) {
            return createNewContext(webSocket);
         } else {
            existing.reattach(webSocket);
            return existing;
         }
      });
      webSocket.closeHandler(nil -> {
         if (context.runCompletionFuture != null) {
            context.runCompletionFuture.cancel(false);
         }
         ScheduledFuture<?> future = SCHEDULED_EXECUTOR.schedule(() -> {
            ClosedContext closedContext = closedRunners.get(context.sessionId);
            if (closedContext != null && closedContext.closed <= System.currentTimeMillis() - SESSION_TIMEOUT) {
               closedContext.context.runner.stop();
               contextMap.remove(context.sessionId);
               closedRunners.remove(context.sessionId);
            }
         }, SESSION_TIMEOUT, TimeUnit.MILLISECONDS);
         closedRunners.put(context.sessionId, new ClosedContext(System.currentTimeMillis(), context, future));
      });

      webSocket.textMessageHandler(msg -> {
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
                  TerminalConnection connection = getConnection(context.runner);
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
            } else if (msg.startsWith(SET_TERM_SIZE)) {
               setTermSize(context, msg.substring(SET_TERM_SIZE.length()));
               return;
            } else if (msg.startsWith(SEND_NOTIFICATIONS)) {
               context.startNotifications();
               return;
            } else if (msg.startsWith(FILE_TRANSFER)) {
               try {
                  synchronized (context) {
                     context.binaryLength = Integer.parseInt(msg.substring(FILE_TRANSFER.length()));
                     // we don't initalize binaryContent here to prevent risk of receiving 'empty' file on interrupt.
                  }
               } catch (NumberFormatException e) {
                  context.outputStream.writeSingleText("Failed to parse file transfer length: closing.");
                  webSocket.close();
               }
               return;
            }
         }
         try {
            context.inputStream.write(msg);
            context.inputStream.flush();
         } catch (IOException e) {
            log.error(new FormattedMessage("Failed to write '{}' to Aesh input", msg), e);
            webSocket.close();
         }
      });
      webSocket.binaryMessageHandler(buffer -> {
         try {
            byte[] bytes = buffer.getBytes();
            synchronized (context) {
               if (context.binaryContent == null) {
                  context.binaryContent = new ByteArrayOutputStream();
               }
               context.binaryContent.write(bytes);
               context.binaryLength -= bytes.length;
            }
            if (context.binaryLength == 0) {
               synchronized (context) {
                  context.latch.countDown();
               }
            } else if (context.binaryLength < 0) {
               log.error("Expected binary input underflow");
               context.outputStream.writeSingleText("ERROR: Expected binary input underflow.");
               webSocket.close();
            }
         } catch (IOException e) {
            log.error("Failed to append bytes", e);
         }
      });
   }

   private void setTermSize(WebCliContext context, String value) {
      String[] dimensions = value.split("x");
      if (dimensions.length == 2) {
         try {
            int width = Integer.parseInt(dimensions[0]);
            int height = Integer.parseInt(dimensions[1]);
            TerminalConnection connection = getConnection(context.runner);
            if (connection != null) {
               ExternalTerminal terminal = (ExternalTerminal) connection.getTerminal();
               Field f = LineDisciplineTerminal.class.getDeclaredField("size");
               f.setAccessible(true);
               f.set(terminal, new Size(width, height));
            }
         } catch (NumberFormatException | NoSuchFieldException | IllegalAccessException e) {
            // ignore
         }
      }
   }

   private WebCliContext createNewContext(ServerWebSocket webSocket) {
      PipedOutputStream pos = new PipedOutputStream();
      PipedInputStream pis;
      try {
         pis = new PipedInputStream(pos);
      } catch (IOException e) {
         log.error("Failed to create input stream", e);
         webSocket.close();
         throw new IllegalStateException(e);
      }
      OutputStreamWriter inputStream = new OutputStreamWriter(pos);
      WebsocketOutputStream stream = new WebsocketOutputStream(webSocket);

      WebCliContext ctx = new WebCliContext(vertx, inputStream, stream, webSocket);
      ctx.setClient(new RestClient(vertx, "localhost", port, ssl, true, null));
      ctx.setOnline(true);

      try {
         var settingsBuilder = settingsBuilder(ctx, new WebCliCommandInvocation.Provider(ctx));
         settingsBuilder.inputStream(pis)
               .persistHistory(false)
               .historySize(Integer.MAX_VALUE)
               .outputStreamError(new PrintStream(stream))
               .outputStream(new PrintStream(stream));
         ctx.runner = configureRunner(ctx, settingsBuilder.build(), null);
      } catch (CommandRegistryException e) {
         throw new IllegalStateException(e);
      }

      webSocket.writeTextMessage("__HYPERFOIL_SESSION_START__\n");
      webSocket.writeTextMessage("Welcome to Hyperfoil! Type 'help' for commands overview.\n");

      Thread cliThread = new Thread(ctx.runner::start, "webcli-" + webSocket.remoteAddress());
      cliThread.setDaemon(true);
      cliThread.start();

      return ctx;
   }

   private TerminalConnection getConnection(AeshConsoleRunner runner) {
      try {
         Field consoleField = AeshConsoleRunner.class.getDeclaredField("console");
         consoleField.setAccessible(true);
         ReadlineConsole console = (ReadlineConsole) consoleField.get(runner);
         if (console == null) {
            return null;
         }
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
      commands.remove(Report.class);
      commands.remove(Run.class);
      commands.remove(RunLocal.class);
      commands.remove(StartLocal.class);
      commands.remove(Upload.class);
      commands.add(Plot.class);
      commands.add(WebEdit.class);
      commands.add(WebExport.class);
      commands.add(WebReport.class);
      commands.add(WebRun.class);
      commands.add(WebUpload.class);
      return commands;
   }

   public void setConnectionOptions(int port, boolean ssl) {
      this.port = port;
      this.ssl = ssl;
   }

   private static class ClosedContext {
      final long closed;
      final WebCliContext context;
      final ScheduledFuture<?> future;

      private ClosedContext(long closed, WebCliContext context, ScheduledFuture<?> future) {
         this.closed = closed;
         this.context = context;
         this.future = future;
      }
   }

}