package io.hyperfoil.clustering.webcli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.commands.BaseReportCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "report", description = "Generate HTML report")
public class WebReport extends BaseReportCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      String report = getReport(invocation);
      invocation.println("Creating report...");
      invocation.println("__HYPERFOIL_DIRECT_DOWNLOAD_MAGIC__");
      invocation.println(getRunRef(invocation).id() + ".html");
      invocation.println(report);
      invocation.println("__HYPERFOIL_DIRECT_DOWNLOAD_END__");
      return CommandResult.SUCCESS;
   }
}
