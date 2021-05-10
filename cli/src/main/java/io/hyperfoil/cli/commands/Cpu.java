package io.hyperfoil.cli.commands;

import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "cpu", description = "Show agent CPU usage")
public class Cpu extends BaseRunIdCommand {

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      Map<String, Map<String, String>> cpu = runRef.agentCpu();
      if (cpu == null || cpu.isEmpty()) {
         invocation.println("No agent CPU data available from run " + runRef.id() + " (maybe not completed yet).");
         return CommandResult.FAILURE;
      }
      Table<Map.Entry<String, Map<String, String>>> table = new Table<>();
      table.column("PHASE", Map.Entry::getKey);
      String[] agents = cpu.values().stream().flatMap(e -> e.keySet().stream()).sorted().distinct().toArray(String[]::new);
      for (String agent : agents) {
         table.column(agent, e -> e.getValue().get(agent));
      }
      table.print(invocation, cpu.entrySet().stream());
      return CommandResult.SUCCESS;
   }
}
