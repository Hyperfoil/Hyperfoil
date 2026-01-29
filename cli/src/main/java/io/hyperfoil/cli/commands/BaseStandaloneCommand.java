package io.hyperfoil.cli.commands;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.registry.CommandRegistry;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCommandInvocationProvider;
import io.hyperfoil.internal.Properties;

public abstract class BaseStandaloneCommand {
   //ignore logging when running in the console below severe
   static {
      Handler[] handlers = Logger.getLogger("").getHandlers();
      for (Handler handler : handlers) {
         handler.setLevel(Level.SEVERE);
      }
   }

   private CommandRegistry<HyperfoilCommandInvocation> commandRegistry;

   protected abstract Class<? extends Command<HyperfoilCommandInvocation>> getCommand();

   protected List<Class<? extends Command<HyperfoilCommandInvocation>>> getDependencyCommands() {
      return List.of();
   }

   protected abstract String getCommandName();

   public int exec(String[] args) {
      CommandRuntime<HyperfoilCommandInvocation> cr = null;
      CommandResult result = null;

      try {
         AeshCommandRuntimeBuilder<HyperfoilCommandInvocation> runtime = AeshCommandRuntimeBuilder.builder();
         runtime.commandInvocationProvider(new HyperfoilCommandInvocationProvider(new HyperfoilCliContext()));

         @SuppressWarnings("unchecked")
         AeshCommandRegistryBuilder<HyperfoilCommandInvocation> registry = AeshCommandRegistryBuilder
               .<HyperfoilCommandInvocation> builder()
               // add default commands
               .commands(StartLocal.class, getCommand(), Exit.class);

         for (Class<? extends Command<HyperfoilCommandInvocation>> command : getDependencyCommands()) {
            registry.command(command);
         }

         commandRegistry = registry.create();
         runtime.commandRegistry(commandRegistry);

         cr = runtime.build();
         try {
            // start the local in-vm controller server
            cr.executeCommand("start-local --quiet");
            // As -H option could contain a whitespace we have to either escape the space or quote the argument.
            // However, quoting would not work well if the argument contains a quote.
            String optionsCollected = Stream.of(args).map(arg -> arg.replaceAll(" ", "\\\\ ")).collect(Collectors.joining(" "));
            result = cr.executeCommand(getCommandName() + " " + optionsCollected);
         } finally {
            // exit from the CLI
            cr.executeCommand("exit");
         }
      } catch (Exception e) {
         System.out.println("Failed to execute command: " + e.getMessage());
         if (Boolean.getBoolean(Properties.HYPERFOIL_STACKTRACE)) {
            e.printStackTrace();
         }

         if (cr != null) {
            try {
               System.out.println(
                     cr.getCommandRegistry().getCommand(getCommandName(), getCommandName()).printHelp(getCommandName()));
            } catch (CommandNotFoundException ex) {
               throw new IllegalStateException(ex);
            }
         }
      }

      return result == null ? CommandResult.FAILURE.getResultValue() : result.getResultValue();
   }

   /**
    * Due to the nature of instances being created in runtime the only way to retrieve is by using the commandRegistry
    *
    * @return
    */
   public CommandRegistry<HyperfoilCommandInvocation> getCommandRegistry() {
      return commandRegistry;
   }
}
