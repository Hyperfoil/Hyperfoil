package io.hyperfoil.cli.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "connect", description = "Connects CLI to Hyperfoil Controller server")
public class Connect implements Command<HyperfoilCommandInvocation> {
   @Argument(description = "Hyperfoil host", defaultValue = "localhost")
   String host;

   @Option(shortName = 'p', name = "port", description = "Hyperfoil port", defaultValue = "8090")
   int port;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
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
      ctx.setClient(new RestClient(host, port));
      try {
         long preMillis = System.currentTimeMillis();
         long serverEpochTime = ctx.client().ping();
         long postMillis = System.currentTimeMillis();
         invocation.println("Connected!");
         if (serverEpochTime != 0 && (serverEpochTime < preMillis || serverEpochTime > postMillis)) {
            invocation.println("WARNING: Server time seems to be off by " + (postMillis + preMillis - 2 * serverEpochTime) / 2 + " ms");
         }
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         ctx.client().close();
         ctx.setClient(null);
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Failed connecting to " + host + ":" + port, e);
      }
   }
}
