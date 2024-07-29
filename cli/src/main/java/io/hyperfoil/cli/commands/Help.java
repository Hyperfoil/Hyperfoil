package io.hyperfoil.cli.commands;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCompleterData;

@CommandDefinition(name = "help", description = "Provides help for other CLI commands.")
public class Help implements Command<HyperfoilCommandInvocation> {
   private static final Table<ProcessedCommand<?, ?>> ALL_COMMANDS = new Table<ProcessedCommand<?, ?>>()
         .column("COMMAND", ProcessedCommand::name)
         .column("DESCRIPTION", ProcessedCommand::description);

   @Argument(description = "Command for which you need help.", completer = CommandCompleter.class)
   String command;
   private Comparator<ProcessedCommand<?, ?>> COMMAND_COMPARATOR = Comparator.comparing(ProcessedCommand::name);

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) {
      if (command == null || command.isEmpty()) {
         invocation.println("Hyperfoil CLI, version " + io.hyperfoil.api.Version.VERSION);
         invocation.println("\nAvailable commands:\n");
         Function<String, ProcessedCommand<?, ?>> toProcessedCommand = c -> {
            try {
               return invocation.context().commandRegistry().getCommand(c, c).getParser().getProcessedCommand();
            } catch (CommandNotFoundException e) {
               throw new IllegalStateException(e);
            }
         };
         ALL_COMMANDS.print(invocation, invocation.context().commandRegistry().getAllCommandNames().stream()
               .map(toProcessedCommand).sorted(COMMAND_COMPARATOR));
         return CommandResult.SUCCESS;
      }
      String help = invocation.getHelpInfo(command);
      if (help == null || help.isEmpty()) {
         invocation.println("No help info available for command '" + command + "'. Available commands: ");
         invocation.println(
               invocation.context().commandRegistry().getAllCommandNames().stream().sorted().collect(Collectors.joining(", ")));
      } else {
         invocation.print(help);
      }
      return CommandResult.SUCCESS;
   }

   private class CommandCompleter implements OptionCompleter<HyperfoilCompleterData> {

      @Override
      public void complete(HyperfoilCompleterData completerInvocation) {
         Stream<String> commands = completerInvocation.getContext().commandRegistry().getAllCommandNames().stream().sorted();
         String prefix = completerInvocation.getGivenCompleteValue();
         if (prefix != null) {
            commands = commands.filter(b -> b.startsWith(prefix));
         }
         commands.forEach(completerInvocation::addCompleterValue);
      }
   }
}
