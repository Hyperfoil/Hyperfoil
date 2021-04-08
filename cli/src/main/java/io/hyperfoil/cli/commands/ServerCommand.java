package io.hyperfoil.cli.commands;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLHandshakeException;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.core.util.Util;

public abstract class ServerCommand implements Command<HyperfoilCommandInvocation> {
   protected static final String MOVE_LINE_UP = new String(new byte[]{ 27, 91, 49, 65 }, StandardCharsets.US_ASCII);
   protected static final String ERASE_WHOLE_LINE = new String(new byte[]{ 27, 91, 50, 75 }, StandardCharsets.US_ASCII);
   protected static final String EDITOR;


   static {
      String editor = System.getenv("VISUAL");
      if (editor == null || editor.isEmpty()) {
         editor = System.getenv("EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = CliUtil.fromCommand("update-alternatives", "--display", "editor");
      }
      if (editor == null || editor.isEmpty()) {
         editor = CliUtil.fromCommand("git", "var", "GIT_EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = "vi";
      }
      EDITOR = editor;
   }

   protected void openInBrowser(String url) throws CommandException {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
         Desktop desktop = Desktop.getDesktop();
         try {
            desktop.browse(new URI(url));
         } catch (IOException | URISyntaxException e) {
            throw new CommandException("Cannot open '" + url + "' in browser: " + Util.explainCauses(e), e);
         }
      } else {
         try {
            Runtime.getRuntime().exec("xdg-open " + url);
         } catch (IOException e) {
            throw new CommandException("Cannot open '" + url + "' in browser: " + Util.explainCauses(e), e);
         }
      }
   }

   protected void ensureConnection(HyperfoilCommandInvocation invocation) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      if (ctx.client() != null) {
         return;
      }
      invocation.println("Not connected, trying to connect to localhost:8090...");
      connect(invocation, false, "localhost", 8090, false, false, null);
   }

   protected void connect(HyperfoilCommandInvocation invocation, boolean quiet, String host, int port, boolean ssl, boolean insecure, String password) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      ctx.setClient(new RestClient(ctx.vertx(), host, port, ssl, insecure, password));
      if (ssl && insecure) {
         invocation.warn("Hyperfoil TLS certificate validity is not checked. Your credentials might get compromised.");
      }
      try {
         long preMillis, postMillis;
         io.hyperfoil.controller.model.Version version;
         for (; ; ) {
            try {
               preMillis = System.currentTimeMillis();
               version = ctx.client().version();
               postMillis = System.currentTimeMillis();
               break;
            } catch (RestClient.Unauthorized e) {
               if (!updatePassword(invocation, ctx)) {
                  return;
               }
            } catch (RestClient.Forbidden e) {
               invocation.println("Provided password is incorrect, please type again:");
               if (!updatePassword(invocation, ctx)) {
                  return;
               }
            } catch (RestClient.RedirectToHost e) {
               ctx.client().close();
               ctx.setClient(null);
               invocation.println("CLI is redirected to " + e.host);
               Connect connect = new Connect();
               connect.host = e.host;
               connect.password = password;
               connect.insecure = insecure;
               connect.execute(invocation);
               return;
            }
         }
         if (!quiet) {
            invocation.println("Connected to " + host + ":" + port + "!");
         }
         ctx.setOnline(true);
         if (!quiet && version.serverTime != null && (version.serverTime.getTime() < preMillis || version.serverTime.getTime() > postMillis)) {
            invocation.warn("Controller time seems to be off by " + (postMillis + preMillis - 2 * version.serverTime.getTime()) / 2 + " ms");
         }
         if (!quiet && !Objects.equals(version.commitId, io.hyperfoil.api.Version.COMMIT_ID)) {
            invocation.warn("Controller version is different from CLI version. Benchmark upload may fail due to binary incompatibility.");
         }
         String shortHost = host;
         if (host.equals(invocation.context().localControllerHost()) && port == invocation.context().localControllerPort()) {
            shortHost = "in-vm";
         } else if (host.contains(".")) {
            shortHost = host.substring(0, host.indexOf('.'));
         }
         invocation.setPrompt(new Prompt(new TerminalString("[hyperfoil@" + shortHost + "]$ ",
               new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
         ctx.setControllerId(null);
         ctx.setControllerPollTask(ctx.executor().scheduleAtFixedRate(() -> {
            try {
               String currentId = ctx.client().version().deploymentId;
               if (!ctx.online()) {
                  invocation.print("\n" + ANSI.GREEN_TEXT + "INFO: Controller is back online." + ANSI.RESET + "\n");
                  ctx.setOnline(true);
               }
               if (ctx.controllerId() == null) {
                  ctx.setControllerId(currentId);
               } else if (!ctx.controllerId().equals(currentId)) {
                  invocation.print("\n" + ANSI.RED_TEXT + ANSI.BOLD + "WARNING: controller was restarted." + ANSI.RESET + "\n");
                  ctx.setControllerId(currentId);
               }
            } catch (RestClientException e) {
               if (ctx.online()) {
                  invocation.print("\n" + ANSI.YELLOW_TEXT + ANSI.BOLD + "WARNING: controller seems offline." + ANSI.RESET + "\n");
                  ctx.setOnline(false);
               }
            }
         }, 0, 15, TimeUnit.SECONDS));
      } catch (RestClientException e) {
         ctx.client().close();
         ctx.setClient(null);
         invocation.error(e);
         if (Objects.equals(e.getCause().getMessage(), "Connection was closed")) {
            invocation.println("Hint: Server might be secured; use --tls.");
         }
         if (e.getCause() instanceof SSLHandshakeException) {
            invocation.println("Hint: TLS certificate verification might have failed. Use --insecure to disable validation.");
         }
         throw new CommandException("Failed connecting to " + host + ":" + port, e);
      }
   }

   private boolean updatePassword(HyperfoilCommandInvocation invocation, HyperfoilCliContext ctx) {
      try {
         String password = invocation.inputLine(new Prompt("password: ", '*'));
         if (password == null || password.isEmpty()) {
            invocation.println("Empty password, not connecting.");
            ctx.client().close();
            ctx.setClient(null);
            return false;
         }
         ctx.client().setPassword(password);
         return true;
      } catch (InterruptedException ie) {
         ctx.client().close();
         ctx.setClient(null);
         invocation.println("Not connected.");
         return false;
      }
   }

   protected void printList(HyperfoilCommandInvocation invocation, Collection<String> items, int limit) {
      int counter = 0;
      for (String name : items) {
         invocation.print(name);
         invocation.print("  ");
         if (counter++ > limit) {
            invocation.print("... (" + (items.size() - 15) + " more)");
            break;
         }
      }
      invocation.println("");
   }

   protected void clearLines(HyperfoilCommandInvocation invocation, int numLines) {
      invocation.print(ERASE_WHOLE_LINE);
      for (int i = 0; i < numLines; ++i) {
         invocation.print(MOVE_LINE_UP);
         invocation.print(ERASE_WHOLE_LINE);
      }
   }

   protected boolean interruptibleDelay(HyperfoilCommandInvocation invocation) {
      invocation.println("Press " + invocation.context().interruptKey() + " to stop watching...");
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {
         clearLines(invocation, 1);
         invocation.println("");
         return true;
      }
      return false;
   }

   protected void failMissingRunId(HyperfoilCommandInvocation invocation) throws CommandException {
      invocation.println("Command '" + getClass().getSimpleName().toLowerCase() + "' requires run ID as argument! Available runs:");
      List<Run> runs = invocation.context().client().runs(false);
      printList(invocation, runs.stream().map(r -> r.id).sorted(Comparator.reverseOrder()).collect(Collectors.toList()), 15);
      throw new CommandException("Cannot run command without run ID.");
   }
}
