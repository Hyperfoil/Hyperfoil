package io.hyperfoil.cli.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;

@CommandDefinition(name = "connect", description = "Connects CLI to Hyperfoil Controller server")
public class Connect implements Command<HyperfoilCommandInvocation> {
   @Option(shortName = 'h', name = "host", description = "Hyperfoil host", defaultValue = "localhost")
   String host;

   @Option(shortName = 'p', name = "port", description = "Hyperfoil port", defaultValue = "8090")
   int port;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation commandInvocation) throws CommandException, InterruptedException {
      HyperfoilCliContext ctx = commandInvocation.context();
      if (ctx.client() != null) {
         if (ctx.client().host().equals(host) && ctx.client().port() == port) {
            commandInvocation.println("Already connected to " + host + ":" + port + ", not reconnecting.");
            return CommandResult.SUCCESS;
         } else {
            commandInvocation.println("Closing connection to " + ctx.client());
            ctx.client().close();
         }
      }
      ctx.setClient(new RestClient(host, port));
      try {
         ctx.client().ping();
         commandInvocation.println("Connected!");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         ctx.client().close();
         ctx.setClient(null);
         throw new CommandException("Failed connecting to " + host + ":" + port, e);
      }
   }
}
