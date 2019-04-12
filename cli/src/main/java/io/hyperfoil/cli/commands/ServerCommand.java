package io.hyperfoil.cli.commands;

import org.aesh.command.Command;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public abstract class ServerCommand implements Command<HyperfoilCommandInvocation> {
   protected boolean ensureConnection(HyperfoilCommandInvocation invocation) {
      if (invocation.context().client() == null) {
         invocation.println("Not connected! Use `connect [-h host] [-p port]`");
         return false;
      }
      return true;
   }
}
