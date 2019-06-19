package io.hyperfoil.cli.commands;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

public abstract class ServerCommand implements Command<HyperfoilCommandInvocation> {
   protected static final String MOVE_LINE_UP = new String(new byte[] {27, 91, 49, 65}, StandardCharsets.US_ASCII);
   protected static final String ERASE_WHOLE_LINE = new String(new byte[]{27, 91, 50, 75}, StandardCharsets.US_ASCII);

   protected void ensureConnection(HyperfoilCommandInvocation invocation) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      if (ctx.client() != null) {
         return;
      }
      invocation.println("Not connected, trying to connect to localhost:8090...");
      connect(invocation, "localhost", 8090);
   }

   protected void connect(HyperfoilCommandInvocation invocation, String host, int port) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      ctx.setClient(new RestClient(host, port));
      try {
         long preMillis = System.currentTimeMillis();
         long serverEpochTime = ctx.client().ping();
         long postMillis = System.currentTimeMillis();
         invocation.println("Connected!");
         if (serverEpochTime != 0 && (serverEpochTime < preMillis || serverEpochTime > postMillis)) {
            invocation.println("WARNING: Server time seems to be off by " + (postMillis + preMillis - 2 * serverEpochTime) / 2 + " ms");
         }
         String shortHost = host.contains(".") ? host.substring(0, host.indexOf('.')) : host;
         invocation.setPrompt(new Prompt(new TerminalString("[hyperfoil@" + shortHost + "]$ ",
               new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
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
}
