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
   private static final int DEFAULT_PORT = 8090;
   @Argument(description = "Hyperfoil host", completer = HostCompleter.class)
   String host;

   @Option(shortName = 'p', description = "Hyperfoil port")
   Integer port;

   @Option(shortName = 't', description = "Use secure (HTTPS/TLS) connections.", hasValue = false)
   boolean tls;

   @Option(name = "no-tls", description = "Do not use (HTTPS/TLS) connections.", hasValue = false)
   boolean noTls;

   @Option(shortName = 'k', description = "Do not verify certificate validity.", hasValue = false)
   boolean insecure;

   @Option(description = "Password used for server access (will be queried if necessary).")
   String password;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      HyperfoilCliContext ctx = invocation.context();
      if (host != null && host.startsWith("http://")) {
         int end = host.indexOf('/', 7);
         host = host.substring(7, end < 0 ? host.length() : end);
         if (port == null) {
            port = 80;
         }
      } else if (host != null && host.startsWith("https://")) {
         int end = host.indexOf('/', 8);
         host = host.substring(8, end < 0 ? host.length() : end);
         if (port == null) {
            port = 443;
         }
         if (!noTls) {
            tls = true;
         }
      }
      if (host != null) {
         int colonIndex = host.indexOf(':');
         if (colonIndex >= 0) {
            String portStr = host.substring(colonIndex + 1);
            try {
               port = Integer.parseInt(portStr);
               host = host.substring(0, colonIndex);
            } catch (NumberFormatException e) {
               invocation.error("Cannot parse port '" + portStr + "'");
               return CommandResult.FAILURE;
            }
         }
      }
      if (port != null && port % 1000 == 443 && !noTls) {
         tls = true;
      }
      if (ctx.client() != null) {
         if (ctx.client().host().equals(host) && (ctx.client().port() == DEFAULT_PORT && port == null || port != null && ctx.client().port() == port)) {
            invocation.println("Already connected to " + ctx.client().host() + ":" + ctx.client().port() + ", not reconnecting.");
            return CommandResult.SUCCESS;
         } else {
            invocation.println("Closing connection to " + ctx.client());
            ctx.client().close();
            ctx.setClient(null);
            ctx.setServerRun(null);
            ctx.setServerBenchmark(null);
            ctx.setOnline(false);
            invocation.setPrompt(new Prompt(new TerminalString("[hyperfoil]$ ",
                  new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT))));
         }
      }
      if (host == null && port == null && invocation.context().localControllerPort() > 0) {
         host = invocation.context().localControllerHost();
         port = invocation.context().localControllerPort();
      } else if (host == null) {
         host = "localhost";
      }
      if (port == null) {
         port = DEFAULT_PORT;
      }
      connect(invocation, false, host, port, tls, insecure, password);
      return CommandResult.SUCCESS;
   }
}
