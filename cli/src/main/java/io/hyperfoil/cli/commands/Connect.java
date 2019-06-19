package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "connect", description = "Connects CLI to Hyperfoil Controller server")
public class Connect extends ServerCommand {
   @Argument(description = "Hyperfoil host", defaultValue = "localhost")
   String host;

   @Option(shortName = 'p', name = "port", description = "Hyperfoil port", defaultValue = "8090")
   int port;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      if (ctx.client() != null) {
         if (ctx.client().host().equals(host) && ctx.client().port() == port) {
            invocation.println("Already connected to " + host + ":" + port + ", not reconnecting.");
            return CommandResult.SUCCESS;
         } else {
            invocation.println("Closing connection to " + ctx.client());
            ctx.client().close();
         }
      }
      connect(invocation, host, port);
      return CommandResult.SUCCESS;
   }
}
