package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "new", description = "Creates a new benchmark")
public class New extends ServerCommand {
   // Reading resource list from folder is too complicated, let's enumerate them here
   private static final String[] TEMPLATES = new String[]{ "constant", "throughput", "empty" };

   @Option(shortName = 't', description = "Template used to set up the benchmark.", completer = TemplateCompleter.class)
   public String template;

   @Argument(description = "Name of the benchmark.", completer = BenchmarkCompleter.class)
   public String benchmark;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      if (template == null) {
         invocation.println("No template was specified, available templates: ");
         invocation.println("    " + String.join(", ", TEMPLATES));
         invocation.print("Please select a template or keep empty for 'empty' benchmark: ");
         try {
            template = invocation.inputLine();
            if (template.isBlank()) {
               template = "empty";
            }
         } catch (InterruptedException e) {
            invocation.println("Cancelled, not creating any benchmark.");
            return CommandResult.FAILURE;
         }
      }
      if (!Arrays.asList(TEMPLATES).contains(template)) {
         invocation.println("Template '" + template + "' is not available, please choose one of: ");
         invocation.println("    " + String.join(", ", TEMPLATES));
         return CommandResult.FAILURE;
      }
      HyperfoilCliContext ctx = invocation.context();
      if (benchmark == null || benchmark.isEmpty()) {
         invocation.println("Must specify benchmark name.");
         invocation.println(invocation.getHelpInfo());
         return CommandResult.FAILURE;
      }
      if (ctx.client().benchmark(benchmark).exists()) {
         invocation.println("Benchmark " + benchmark + " already exists.");
         return CommandResult.FAILURE;
      }
      ctx.setCurrentParams(Collections.emptyMap());
      InputStream resourceStream = getClass().getResourceAsStream("/benchmark-templates/" + template + ".yaml");
      if (resourceStream == null) {
         invocation.error("Template " + template + " was not found");
         return CommandResult.FAILURE;
      }
      try {
         String yaml = Util.toString(resourceStream).replace("!param NAME", benchmark);
         Client.BenchmarkRef benchmarkRef = ctx.client().register(yaml, Collections.emptyMap(), null, null);
         ctx.setServerBenchmark(benchmarkRef);
      } catch (IOException e) {
         invocation.error(e);
         return CommandResult.FAILURE;
      }
      try {
         invocation.executeCommand("edit " + benchmark);
         return CommandResult.SUCCESS;
      } catch (Exception e) {
         invocation.error("Cannot execute 'edit'", e);
         return CommandResult.FAILURE;
      }
   }

   public static class TemplateCompleter implements OptionCompleter<CompleterInvocation> {
      @Override
      public void complete(CompleterInvocation completerInvocation) {
         String prefix = completerInvocation.getGivenCompleteValue();
         for (String template : TEMPLATES) {
            if (template.startsWith(prefix)) {
               completerInvocation.addCompleterValue(template);
            }
         }
      }
   }
}
