package io.hyperfoil.cli.commands;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.aesh.command.Command;
import org.aesh.command.CommandException;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public abstract class ServerCommand implements Command<HyperfoilCommandInvocation> {
   protected static final String MOVE_LINE_UP = new String(new byte[] {27, 91, 49, 65}, StandardCharsets.US_ASCII);
   protected static final String ERASE_WHOLE_LINE = new String(new byte[]{27, 91, 50, 75}, StandardCharsets.US_ASCII);

   protected void ensureConnection(HyperfoilCommandInvocation invocation) throws CommandException {
      if (invocation.context().client() == null) {
         throw new CommandException("Not connected! Use `connect [-h host] [-p port]`");
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
}
