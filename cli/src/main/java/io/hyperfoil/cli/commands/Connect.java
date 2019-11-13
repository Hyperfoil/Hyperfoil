package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.readline.Prompt;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.readline.terminal.formatting.TerminalString;

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
            invocation.setPrompt(new Prompt(new TerminalString("[hyperfoil]$ ",
                  new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
         }
      }
      if ("localhost".equals(host) && port == 8090 && invocation.context().localControllerPort() > 0) {
         host = invocation.context().localControllerHost();
         port = invocation.context().localControllerPort();
      }
      connect(invocation, host, port);
      return CommandResult.SUCCESS;
   }
}
