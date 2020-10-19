package io.hyperfoil.cli.commands;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

public abstract class ServerCommand implements Command<HyperfoilCommandInvocation> {
   protected static final String MOVE_LINE_UP = new String(new byte[]{ 27, 91, 49, 65 }, StandardCharsets.US_ASCII);
   protected static final String ERASE_WHOLE_LINE = new String(new byte[]{ 27, 91, 50, 75 }, StandardCharsets.US_ASCII);
   protected static final String EDITOR;
   protected static final String PAGER;

   static {
      String editor = System.getenv("VISUAL");
      if (editor == null || editor.isEmpty()) {
         editor = System.getenv("EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = fromCommand("update-alternatives", "--display", "editor");
      }
      if (editor == null || editor.isEmpty()) {
         editor = fromCommand("git", "var", "GIT_EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = "vi";
      }
      EDITOR = editor;

      String pager = System.getenv("PAGER");
      if (pager == null || pager.isEmpty()) {
         pager = fromCommand("update-alternatives", "--display", "pager");
      }
      if (pager == null || pager.isEmpty()) {
         pager = fromCommand("git", "var", "GIT_PAGER");
      }
      if (pager == null || pager.isEmpty()) {
         pager = "less";
      }
      PAGER = pager;
   }

   private static String fromCommand(String... command) {
      String editor = null;
      try {
         Process gitEditor = new ProcessBuilder(command).start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(gitEditor.getInputStream()))) {
            editor = reader.readLine();
         }
         gitEditor.destroy();
      } catch (IOException e) {
         // ignore error
      }
      return editor;
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
      connect(invocation, "localhost", 8090, false);
   }

   protected void connect(HyperfoilCommandInvocation invocation, String host, int port, boolean quiet) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      ctx.setClient(new RestClient(ctx.vertx(), host, port));
      try {
         long preMillis = System.currentTimeMillis();
         io.hyperfoil.controller.model.Version version = ctx.client().version();
         long postMillis = System.currentTimeMillis();
         if (!quiet) {
            invocation.println("Connected!");
         }
         ctx.setOnline(true);
         if (!quiet && version.serverTime != null && (version.serverTime.getTime() < preMillis || version.serverTime.getTime() > postMillis)) {
            invocation.println(ANSI.YELLOW_TEXT + "WARNING: Controller time seems to be off by " + (postMillis + preMillis - 2 * version.serverTime.getTime()) / 2 + " ms" + ANSI.RESET);
         }
         if (!quiet && !Objects.equals(version.commitId, io.hyperfoil.api.Version.COMMIT_ID)) {
            invocation.println(ANSI.YELLOW_TEXT + "WARNING: Controller version is different from CLI version. Benchmark upload may fail due to binary incompatibility." + ANSI.RESET);
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
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Failed connecting to " + host + ":" + port, e);
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
      invocation.println("Press Ctrl+C to stop watching...");
      try {
         Thread.sleep(1000);
      } catch (InterruptedException e) {
         clearLines(invocation, 1);
         invocation.println("");
         return true;
      }
      return false;
   }

   protected void execProcess(HyperfoilCommandInvocation invocation, boolean expectNewWindow, String command, String... params) throws IOException {
      Process process = null;
      try {
         if (expectNewWindow) {
            invocation.println("Press Ctrl+C when done...");
         }
         ArrayList<String> cmdline = new ArrayList<>();
         cmdline.addAll(Arrays.asList(command.split("[\t \n]+", 0)));
         cmdline.addAll(Arrays.asList(params));
         process = new ProcessBuilder(cmdline.toArray(new String[0])).inheritIO().start();
         process.waitFor();
      } catch (InterruptedException e) {
         if (process != null) {
            process.destroy();
         }
      }
   }
}
