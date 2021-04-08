package io.hyperfoil.clustering.webcli;

import java.nio.charset.StandardCharsets;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.commands.BaseRunIdCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.vertx.core.buffer.Buffer;

@CommandDefinition(name = "report", description = "Generate HTML report")
public class WebReport extends BaseRunIdCommand {
   @Option(shortName = 's', description = "Other file (in given run) to use as report input.")
   protected String source;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      String report = new String(runRef.report(source), StandardCharsets.UTF_8);
      invocation.println("Creating report...");
      invocation.println("__HYPERFOIL_DIRECT_DOWNLOAD_MAGIC__");
      invocation.println(runRef.id() + ".html");
      ((WebCliContext) invocation.context()).sendBinaryMessage(Buffer.buffer(report));
      invocation.println("__HYPERFOIL_DIRECT_DOWNLOAD_END__");
      return CommandResult.SUCCESS;
   }
}
